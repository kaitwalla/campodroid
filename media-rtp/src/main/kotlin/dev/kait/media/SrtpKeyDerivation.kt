package dev.kait.media

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Default RFC 3711 AES-CM 128-bit SRTP key schedule, key derivation rate = 0. */
object SrtpKeyDerivation {
    data class SessionKeys(val encryption: ByteArray, val authentication: ByteArray, val salt: ByteArray)

    fun derive(masterKey: ByteArray, masterSalt: ByteArray): SessionKeys {
        require(masterKey.size == 16) { "AES_CM_128 needs a 16-byte master key" }
        require(masterSalt.size == 14) { "AES_CM_128 needs a 14-byte master salt" }
        return SessionKeys(
            deriveLabel(masterKey, masterSalt, 0, 16),
            deriveLabel(masterKey, masterSalt, 1, 20),
            deriveLabel(masterKey, masterSalt, 2, 14),
        )
    }

    /** RFC 3711 SRTCP uses independent derivation labels 3, 4 and 5. */
    fun deriveRtcp(masterKey: ByteArray, masterSalt: ByteArray): SessionKeys {
        require(masterKey.size == 16 && masterSalt.size == 14)
        return SessionKeys(
            deriveLabel(masterKey, masterSalt, 3, 16),
            deriveLabel(masterKey, masterSalt, 4, 20),
            deriveLabel(masterKey, masterSalt, 5, 14),
        )
    }

    private fun deriveLabel(key: ByteArray, salt: ByteArray, label: Int, size: Int): ByteArray {
        val iv = ByteArray(16)
        salt.copyInto(iv)
        // RFC 3711 section 4.3: x = master_salt XOR (label || 0x000000000000).
        // With 14-byte master_salt, the label occupies octet 7; IV appends 0x0000.
        iv[7] = (iv[7].toInt() xor label).toByte()
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(ByteArray(size))
    }
}
