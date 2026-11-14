package dev.kait.hap

/** Controller key record. Production caller must persist atomically in encrypted storage. */
data class TrustedController(val identifier: String, val publicKey: ByteArray, val admin: Boolean)

interface TrustedControllerStore {
    fun isPaired(): Boolean
    /** Must fail if a concurrent pairing has already been stored. */
    fun addFirstController(controller: TrustedController): Boolean
}

data class AccessorySigningIdentity(val identifier: String, val signingSeed: ByteArray) {
    init { require(identifier.isNotBlank() && signingSeed.size == 32) }
    val publicKey: ByteArray get() = HapCrypto.ed25519PublicKey(signingSeed)
}

/** HAP unpaired M1→M6 exchange; instantiate once per active connection. Not an HTTP server. */
class PairSetupExchange(
    private val code: String,
    private val accessory: AccessorySigningIdentity,
    private val store: TrustedControllerStore,
    private val throttle: PairingThrottle,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val srpFactory: (String) -> SrpServer = { SrpServer(it) },
) {
    private enum class Phase { INITIAL, WAIT_M3, WAIT_M5, DONE }
    private var phase = Phase.INITIAL
    private var srp: SrpServer? = null
    private var sessionKey: ByteArray? = null

    @Synchronized fun handle(input: ByteArray): ByteArray {
        val fields = try {
            val elements = Tlv8.decode(input)
            // Exactly one distinct value per field after fragmented TLV reassembly.
            val mapped = elements.groupBy { it.first }
            require(mapped.values.all { it.size == 1 })
            mapped.mapValues { it.value.single().second }
        } catch (_: IllegalArgumentException) {
            return fail(2, 1)
        }
        val state = fields[STATE]?.singleOrNull()?.toInt() ?: return fail(2, 1)
        return when (state) {
            1 -> m1(fields)
            3 -> m3(fields)
            5 -> m5(fields)
            else -> fail(2, 1)
        }
    }

    private fun m1(fields: Map<Int, ByteArray>): ByteArray {
        if (phase != Phase.INITIAL) return fail(2, 7)
        if (fields[METHOD]?.contentEquals(byteArrayOf(0)) != true) return fail(2, 1)
        if (!throttle.canAttempt(nowMs())) return fail(2, 5)
        if (store.isPaired()) return fail(2, 6)
        val server = srpFactory(code)
        srp = server
        phase = Phase.WAIT_M3
        return response(2, PUBLIC_KEY to server.publicKey, SALT to server.publicSalt)
    }

    private fun m3(fields: Map<Int, ByteArray>): ByteArray {
        val server = srp ?: return fail(4, 2)
        if (phase != Phase.WAIT_M3) return fail(4, 2)
        val clientKey = fields[PUBLIC_KEY] ?: return fail(4, 2)
        val proof = fields[PROOF] ?: return fail(4, 2)
        if (!server.acceptClientPublicKey(clientKey)) return badProof(4)
        val serverProof = server.verifyClientProof(proof) ?: return badProof(4)
        sessionKey = server.sessionKey()
        srp = null
        phase = Phase.WAIT_M5
        return response(4, PROOF to serverProof)
    }

    private fun m5(fields: Map<Int, ByteArray>): ByteArray {
        val key = sessionKey ?: return fail(6, 2)
        if (phase != Phase.WAIT_M5) return fail(6, 2)
        return try {
            val encrypted = fields[ENCRYPTED] ?: return fail(6, 2)
            val encryptionKey = HapCrypto.hkdf(key, "Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info")
            val decoded = Tlv8.decode(HapCrypto.open(encryptionKey, "PS-Msg05", encrypted))
            val fields = decoded.associate { it.first to it.second }
            if (decoded.map { it.first }.distinct().size != decoded.size) return fail(6, 2)
            val identifierBytes = fields[IDENTIFIER] ?: return fail(6, 2)
            val identifier = identifierBytes.toString(Charsets.UTF_8)
            val publicKey = fields[PUBLIC_KEY] ?: return fail(6, 2)
            val signature = fields[SIGNATURE] ?: return fail(6, 2)
            if (identifier.isBlank() || publicKey.size != 32 || signature.size != 64) return fail(6, 2)
            val controllerX = HapCrypto.hkdf(key, "Pair-Setup-Controller-Sign-Salt", "Pair-Setup-Controller-Sign-Info")
            if (!HapCrypto.verifyEd25519(publicKey, controllerX + identifierBytes + publicKey, signature)) return fail(6, 2)

            val accessoryId = accessory.identifier.toByteArray(Charsets.UTF_8)
            val accessoryX = HapCrypto.hkdf(key, "Pair-Setup-Accessory-Sign-Salt", "Pair-Setup-Accessory-Sign-Info")
            val accessoryPublicKey = accessory.publicKey
            val accessorySignature = HapCrypto.signEd25519(accessory.signingSeed, accessoryX + accessoryId + accessoryPublicKey)
            val sub = Tlv8.encode(listOf(
                IDENTIFIER to accessoryId, PUBLIC_KEY to accessoryPublicKey, SIGNATURE to accessorySignature,
            ))
            val sealed = HapCrypto.seal(encryptionKey, "PS-Msg06", sub)
            if (!store.addFirstController(TrustedController(identifier, publicKey.copyOf(), true))) return fail(6, 6)
            phase = Phase.DONE
            sessionKey = null
            response(6, ENCRYPTED to sealed)
        } catch (_: Exception) { fail(6, 2) }
    }

    private fun badProof(responseState: Int): ByteArray {
        throttle.recordInvalidProof(nowMs())
        return fail(responseState, 2)
    }

    private fun fail(responseState: Int, error: Int): ByteArray {
        phase = Phase.DONE
        srp = null
        sessionKey?.fill(0)
        sessionKey = null
        return response(responseState, ERROR to byteArrayOf(error.toByte()))
    }

    private fun response(state: Int, vararg fields: Pair<Int, ByteArray>): ByteArray =
        Tlv8.encode(listOf(STATE to byteArrayOf(state.toByte())) + fields)

    companion object {
        const val METHOD = 0
        const val IDENTIFIER = 1
        const val SALT = 2
        const val PUBLIC_KEY = 3
        const val PROOF = 4
        const val ENCRYPTED = 5
        const val STATE = 6
        const val ERROR = 7
        const val SIGNATURE = 10
    }
}
