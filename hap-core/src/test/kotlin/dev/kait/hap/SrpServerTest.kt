package dev.kait.hap

import java.math.BigInteger
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SrpServerTest {
    private val server = SrpServer(
        "249-52-118",
        salt = ByteArray(16) { (it + 1).toByte() },
        privateKey = ByteArray(32) { (it + 2).toByte() },
    )

    @Test fun exposesCorrectlySizedM2() {
        assertEquals(16, server.publicSalt.size)
        assertEquals(384, server.publicKey.size)
        assertNull(server.sessionKey())
    }

    @Test fun rejectsInvalidOrReplayedPublicKeys() {
        assertFalse(server.acceptClientPublicKey(ByteArray(1)))
        assertFalse(server.acceptClientPublicKey(ByteArray(384)))
        assertTrue(server.acceptClientPublicKey(ByteArray(384).apply { this[383] = 5 }))
        assertFalse(server.acceptClientPublicKey(ByteArray(384).apply { this[383] = 7 }))
        assertNull(server.sessionKey())
    }

    @Test fun rejectsWrongProofWithoutExposingKey() {
        assertTrue(server.acceptClientPublicKey(ByteArray(384).apply { this[383] = 5 }))
        assertNull(server.verifyClientProof(ByteArray(64) { 7 }))
        assertNull(server.sessionKey())
    }

    @Test fun endToEndHandshakeWithIndependentClient() {
        val n = BigInteger(N_HEX, 16)
        val g = BigInteger.valueOf(5)
        val a = BigInteger("102030405060708090a0b0c0d0e0f0", 16)
        val clientPublic = g.modPow(a, n)
        val serverPublic = BigInteger(1, server.publicKey)
        val pad: (BigInteger) -> ByteArray = { v ->
            val encoded = v.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.drop(1).toByteArray() else it }
            ByteArray(384 - encoded.size) + encoded
        }
        val hash: (Array<out ByteArray>) -> ByteArray = { parts ->
            MessageDigest.getInstance("SHA-512").apply { parts.forEach { update(it) } }.digest()
        }
        fun h(vararg parts: ByteArray) = hash(parts)
        val k = BigInteger(1, h(pad(n), pad(g)))
        val u = BigInteger(1, h(pad(clientPublic), server.publicKey))
        val x = BigInteger(1, h(server.publicSalt, h("Pair-Setup:249-52-118".toByteArray())))
        val secret = serverPublic.subtract(k.multiply(g.modPow(x, n))).mod(n)
            .modPow(a.add(u.multiply(x)), n)
        val key = h(pad(secret))
        val hn = h(n.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it })
        val hg = h(byteArrayOf(5))
        val xor = ByteArray(64) { (hn[it].toInt() xor hg[it].toInt()).toByte() }
        val proof = h(xor, h("Pair-Setup".toByteArray()), server.publicSalt, pad(clientPublic), server.publicKey, key)

        assertTrue(server.acceptClientPublicKey(pad(clientPublic)))
        val m4 = assertNotNull(server.verifyClientProof(proof))
        assertContentEquals(h(pad(clientPublic), proof, key), m4)
        assertContentEquals(key, server.sessionKey())
        assertNull(server.verifyClientProof(proof)) // replay rejected
    }

    companion object {
        // Independent RFC5054 3072-bit fixture; duplicated intentionally from the implementation.
        private val N_HEX = "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1" +
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
            "43DB5BFCE0FD108E4B82D120A93AD2CAFFFFFFFFFFFFFFFF"
    }
}
