package dev.kait.hap

/** Established HAP encryption keys are named from the accessory's perspective. */
data class VerifiedSession(
    val controllerIdentifier: String,
    val readKey: ByteArray,
    val writeKey: ByteArray,
    val sharedSecret: ByteArray,
)

/** M1–M4 pair verification. Encryption is enabled by the transport *after* plaintext M4 is sent. */
class PairVerifyExchange(
    private val identity: AccessorySigningIdentity,
    private val findController: (String) -> TrustedController?,
    private val ephemeralFactory: () -> HapCrypto.EphemeralKey = { HapCrypto.generateX25519() },
) {
    private enum class Phase { INITIAL, WAIT_M3, DONE }
    private var phase = Phase.INITIAL
    private var controllerEphemeral: ByteArray? = null
    private var accessoryEphemeral: ByteArray? = null
    private var sharedSecret: ByteArray? = null
    private var sessionKey: ByteArray? = null
    var verifiedSession: VerifiedSession? = null
        private set

    @Synchronized fun handle(input: ByteArray): ByteArray {
        val fields = try {
            val decoded = Tlv8.decode(input)
            require(decoded.map { it.first }.distinct().size == decoded.size)
            decoded.associate { it.first to it.second }
        } catch (_: IllegalArgumentException) { return fail(2) }
        return when (fields[PairSetupExchange.STATE]?.singleOrNull()?.toInt()) {
            1 -> m1(fields)
            3 -> m3(fields)
            else -> fail(2)
        }
    }

    private fun m1(fields: Map<Int, ByteArray>): ByteArray {
        if (phase != Phase.INITIAL) return fail(2)
        val controller = fields[PairSetupExchange.PUBLIC_KEY] ?: return fail(2)
        if (controller.size != 32) return fail(2)
        return try {
            val ephemeral = ephemeralFactory()
            val secret = HapCrypto.agreeX25519(ephemeral.privateKey, controller)
            val key = HapCrypto.hkdf(secret, "Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info")
            val id = identity.identifier.toByteArray(Charsets.UTF_8)
            val signature = HapCrypto.signEd25519(identity.signingSeed, ephemeral.publicKey + id + controller)
            val sub = Tlv8.encode(listOf(
                PairSetupExchange.IDENTIFIER to id,
                PairSetupExchange.SIGNATURE to signature,
            ))
            controllerEphemeral = controller.copyOf()
            accessoryEphemeral = ephemeral.publicKey
            sharedSecret = secret
            sessionKey = key
            phase = Phase.WAIT_M3
            response(2,
                PairSetupExchange.PUBLIC_KEY to ephemeral.publicKey,
                PairSetupExchange.ENCRYPTED to HapCrypto.seal(key, "PV-Msg02", sub),
            )
        } catch (_: Exception) { fail(2) }
    }

    private fun m3(fields: Map<Int, ByteArray>): ByteArray {
        if (phase != Phase.WAIT_M3) return fail(4)
        // This state can never be used to verify another controller after an error.
        phase = Phase.DONE
        return try {
            val encrypted = fields[PairSetupExchange.ENCRYPTED] ?: return fail(4)
            val plaintext = HapCrypto.open(requireNotNull(sessionKey), "PV-Msg03", encrypted)
            val parsed = Tlv8.decode(plaintext)
            if (parsed.map { it.first }.distinct().size != parsed.size) return fail(4)
            val sub = parsed.associate { it.first to it.second }
            val idBytes = sub[PairSetupExchange.IDENTIFIER] ?: return fail(4)
            val id = idBytes.toString(Charsets.UTF_8)
            if (id.isBlank()) return fail(4)
            val stored = findController(id) ?: return fail(4)
            val signature = sub[PairSetupExchange.SIGNATURE] ?: return fail(4)
            val signed = requireNotNull(controllerEphemeral) + idBytes + requireNotNull(accessoryEphemeral)
            if (!HapCrypto.verifyEd25519(stored.publicKey, signed, signature)) return fail(4)
            val secret = requireNotNull(sharedSecret)
            verifiedSession = VerifiedSession(
                id,
                HapCrypto.hkdf(secret, "Control-Salt", "Control-Write-Encryption-Key"),
                HapCrypto.hkdf(secret, "Control-Salt", "Control-Read-Encryption-Key"),
                secret.copyOf(),
            )
            response(4)
        } catch (_: Exception) { fail(4) }
    }

    private fun fail(state: Int): ByteArray {
        phase = Phase.DONE
        verifiedSession = null
        sessionKey?.fill(0)
        sessionKey = null
        return response(state, PairSetupExchange.ERROR to byteArrayOf(2))
    }

    private fun response(state: Int, vararg fields: Pair<Int, ByteArray>): ByteArray =
        Tlv8.encode(listOf(PairSetupExchange.STATE to byteArrayOf(state.toByte())) + fields)
}
