package dev.kait.hap

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/** HAP SRP-6a server: 3072-bit RFC5054 group, SHA-512, username Pair-Setup. */
class SrpServer(
    private val password: String,
    private val salt: ByteArray = ByteArray(16).also(SecureRandom()::nextBytes),
    private val privateKey: ByteArray = ByteArray(32).also(SecureRandom()::nextBytes),
) {
    init {
        require(salt.size == 16)
        require(privateKey.size == 32 && privateKey.any { it.toInt() != 0 })
    }

    private val verifier = G.modPow(
        positive(digest(salt, digest("Pair-Setup:$password".toByteArray(Charsets.UTF_8)))), N
    )
    private val b = positive(privateKey)
    private val k = positive(digest(pad(N), pad(G)))
    private val serverPublic = (k.multiply(verifier).add(G.modPow(b, N))).mod(N)

    /** M2 (server response): provide 16-byte salt and 384-byte public key. */
    val publicKey: ByteArray get() = pad(serverPublic)
    val publicSalt: ByteArray get() = salt.copyOf()

    private var clientPublic: BigInteger? = null
    private var sharedSecret: BigInteger? = null
    private var authenticatedKey: ByteArray? = null

    @Synchronized fun acceptClientPublicKey(bytes: ByteArray): Boolean {
        if (clientPublic != null || bytes.size != LENGTH) return false
        val a = positive(bytes)
        if (a.mod(N) == BigInteger.ZERO) return false
        val u = positive(digest(pad(a), pad(serverPublic)))
        if (u == BigInteger.ZERO) return false
        val s = a.multiply(verifier.modPow(u, N)).mod(N).modPow(b, N)
        clientPublic = a
        sharedSecret = s
        return true
    }

    /** Verify M3 proof; return M4 proof only on successful authentication. */
    @Synchronized fun verifyClientProof(proof: ByteArray): ByteArray? {
        val a = clientPublic ?: return null
        val s = sharedSecret ?: return null
        if (authenticatedKey != null || proof.size != 64) return null
        val sessionKey = digest(pad(s))
        val hashN = digest(unsigned(N))
        val hashG = digest(unsigned(G))
        val hashXor = ByteArray(hashN.size) { (hashN[it].toInt() xor hashG[it].toInt()).toByte() }
        val expected = digest(hashXor, digest("Pair-Setup".toByteArray()), salt, pad(a), pad(serverPublic), sessionKey)
        if (!MessageDigest.isEqual(expected, proof)) return null
        authenticatedKey = sessionKey
        return digest(pad(a), expected, sessionKey)
    }

    /** 64-byte SRP session key, unavailable until a correct proof was received. */
    @Synchronized fun sessionKey(): ByteArray? = authenticatedKey?.copyOf()

    companion object {
        const val LENGTH = 384
        private val G = BigInteger.valueOf(5)
        private val N = BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
                "29024E088A67CC74020BBEA63B139B22514A08798E3404DD" +
                "EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245" +
                "E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
                "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D" +
                "C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F" +
                "83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
                "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
                "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9" +
                "DE2BCBF6955817183995497CEA956AE515D2261898FA0510" +
                "15728E5A8AAAC42DAD33170D04507A33A85521ABDF1CBA64" +
                "ECFB850458DBEF0A8AEA71575D060C7DB3970F85A6E1E4C7" +
                "ABF5AE8CDB0933D71E8C94E04A25619DCEE3D2261AD2EE6B" +
                "F12FFA06D98A0864D87602733EC86A64521F2B18177B200C" +
                "BBE117577A615D6C770988C0BAD946E208E24FA074E5AB31" +
                "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF", 16
        )

        internal fun positive(bytes: ByteArray): BigInteger = BigInteger(1, bytes)
        internal fun unsigned(value: BigInteger): ByteArray = value.toByteArray().let {
            if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }
        internal fun pad(value: BigInteger): ByteArray {
            val raw = unsigned(value)
            require(raw.size <= LENGTH)
            return ByteArray(LENGTH - raw.size) + raw
        }
        internal fun digest(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").apply {
            parts.forEach(::update)
        }.digest()
    }
}
