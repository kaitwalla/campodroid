package dev.kait.pockethomecam

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.kait.hap.AccessorySigningIdentity
import dev.kait.hap.SetupIdentity
import dev.kait.hap.SetupIdentityFactory
import dev.kait.hap.TrustedController
import dev.kait.hap.TrustedControllerStore
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Single-phone identity, encrypted at rest using an Android Keystore AES-GCM key. */
class HapIdentityRepository(context: Context) : TrustedControllerStore {
    private val prefs = context.getSharedPreferences("hap_identity", Context.MODE_PRIVATE)
    private val random = SecureRandom()
    private val key: SecretKey = loadOrCreateKey()

    private fun loadOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }

    private fun decrypt(): JSONObject? {
        val value = prefs.getString("blob", null) ?: return null
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size >= 12 + 16) { "Invalid stored identity" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }

    private fun write(data: JSONObject) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val bytes = cipher.iv + cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("blob", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) {
            "Failed to persist HomeKit identity"
        }
    }

    private fun initialData(): JSONObject {
        val setup = SetupIdentityFactory.generate(random)
        val hardwareId = ByteArray(6).also(random::nextBytes)
            .joinToString(":") { "%02X".format(it.toInt() and 255) }
        val seed = ByteArray(32).also(random::nextBytes)
        return JSONObject()
            .put("setupCode", setup.code)
            .put("setupId", setup.setupId)
            .put("deviceId", hardwareId)
            .put("signingSeed", Base64.encodeToString(seed, Base64.NO_WRAP))
            .put("controllers", JSONArray())
    }

    @Synchronized private fun readOrCreate(): JSONObject = decrypt() ?: initialData().also(::write)

    @Synchronized fun setupIdentity(): SetupIdentity = readOrCreate().let {
        SetupIdentity(it.getString("setupCode"), it.getString("setupId"))
    }

    @Synchronized fun signingIdentity(): AccessorySigningIdentity = readOrCreate().let {
        AccessorySigningIdentity(it.getString("deviceId"), Base64.decode(it.getString("signingSeed"), Base64.NO_WRAP))
    }

    @Synchronized override fun isPaired(): Boolean = readOrCreate().getJSONArray("controllers").length() > 0

    @Synchronized override fun addFirstController(controller: TrustedController): Boolean {
        val root = readOrCreate()
        val current = root.getJSONArray("controllers")
        if (current.length() != 0) return false
        require(controller.identifier.isNotBlank() && controller.publicKey.size == 32 && controller.admin)
        current.put(JSONObject().put("id", controller.identifier)
            .put("public", Base64.encodeToString(controller.publicKey, Base64.NO_WRAP))
            .put("admin", true))
        write(root)
        return true
    }

    @Synchronized fun findController(id: String): TrustedController? {
        val controllers = readOrCreate().getJSONArray("controllers")
        for (i in 0 until controllers.length()) {
            val entry = controllers.getJSONObject(i)
            if (entry.getString("id").equals(id, ignoreCase = true)) {
                return TrustedController(entry.getString("id"),
                    Base64.decode(entry.getString("public"), Base64.NO_WRAP), entry.getBoolean("admin"))
            }
        }
        return null
    }

    companion object { private const val KEY_ALIAS = "campodroid-hap-identity-v1" }
}
