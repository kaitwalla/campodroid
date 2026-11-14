package dev.kait.hap

import java.math.BigInteger
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFails

class PairSetupExchangeTest {
    private class MemoryStore : TrustedControllerStore {
        var controller: TrustedController? = null
        override fun isPaired() = controller != null
        override fun addFirstController(controller: TrustedController): Boolean {
            if (isPaired()) return false
            this.controller = controller
            return true
        }
    }

    private fun newExchange(store: MemoryStore = MemoryStore()) = PairSetupExchange(
        "249-52-118", AccessorySigningIdentity("AA:BB:CC:DD:EE:FF", ByteArray(32) { 4 }),
        store, PairingThrottle(),
    )

    private fun request(state: Int, vararg fields: Pair<Int, ByteArray>): ByteArray =
        Tlv8.encode(listOf(PairSetupExchange.STATE to byteArrayOf(state.toByte())) + fields)

    private fun response(bytes: ByteArray): Map<Int, ByteArray> = Tlv8.decode(bytes).associate { it.first to it.second }

    @Test fun m1ReturnsSaltAnd3072BitPublicKey() {
        val m2 = response(newExchange().handle(request(1, PairSetupExchange.METHOD to byteArrayOf(0))))
        assertContentEquals(byteArrayOf(2), m2[PairSetupExchange.STATE])
        assertEquals(16, m2[PairSetupExchange.SALT]?.size)
        assertEquals(384, m2[PairSetupExchange.PUBLIC_KEY]?.size)
    }

    @Test fun m1RejectsUnsupportedMethod() {
        val m2 = response(newExchange().handle(request(1, PairSetupExchange.METHOD to byteArrayOf(1))))
        assertContentEquals(byteArrayOf(1), m2[PairSetupExchange.ERROR])
    }

    @Test fun failsAndClosesOnBadClientProof() {
        val exchange = newExchange()
        exchange.handle(request(1, PairSetupExchange.METHOD to byteArrayOf(0)))
        val m4 = response(exchange.handle(request(3,
            PairSetupExchange.PUBLIC_KEY to ByteArray(384).apply { this[383] = 5 },
            PairSetupExchange.PROOF to ByteArray(64) { 9 },
        )))
        assertContentEquals(byteArrayOf(2), m4[PairSetupExchange.ERROR])
        val repeat = response(exchange.handle(request(1, PairSetupExchange.METHOD to byteArrayOf(0))))
        assertTrue(repeat.containsKey(PairSetupExchange.ERROR))
    }

    @Test fun rejectsAlreadyPairedAccessory() {
        val store = MemoryStore()
        store.addFirstController(TrustedController("existing", ByteArray(32), true))
        val m2 = response(newExchange(store).handle(request(1, PairSetupExchange.METHOD to byteArrayOf(0))))
        assertContentEquals(byteArrayOf(6), m2[PairSetupExchange.ERROR])
    }

    @Test fun encryptedSubTLVRoundTripAndTamperDetection() {
        val key = HapCrypto.hkdf(ByteArray(64) { it.toByte() }, "Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info")
        val plain = request(5, PairSetupExchange.IDENTIFIER to "test".toByteArray())
        val sealed = HapCrypto.seal(key, "PS-Msg05", plain)
        assertContentEquals(plain, HapCrypto.open(key, "PS-Msg05", sealed))
        val tampered = sealed.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertFails { HapCrypto.open(key, "PS-Msg05", tampered) }
    }

    @Test fun ed25519SigningAndVerification() {
        val seed = ByteArray(32) { (it * 3).toByte() }
        val publicKey = HapCrypto.ed25519PublicKey(seed)
        val payload = "controller identity".toByteArray()
        val signature = HapCrypto.signEd25519(seed, payload)
        assertTrue(HapCrypto.verifyEd25519(publicKey, payload, signature))
        assertFalse(HapCrypto.verifyEd25519(publicKey, "tampered".toByteArray(), signature))
    }

    @Test fun completesM1ThroughM6WithIndependentController() {
        val store = MemoryStore()
        val exchange = newExchange(store)
        val m2 = response(exchange.handle(request(1, PairSetupExchange.METHOD to byteArrayOf(0))))
        val salt = assertNotNull(m2[PairSetupExchange.SALT])
        val bBytes = assertNotNull(m2[PairSetupExchange.PUBLIC_KEY])
        val n = BigInteger(SrpServerTest.N_HEX, 16)
        val g = BigInteger.valueOf(5)
        val a = BigInteger("102030405060708090a0b0c0d0e0f0", 16)
        fun pad(numeric: BigInteger): ByteArray {
            val raw = numeric.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
            return ByteArray(384 - raw.size) + raw
        }
        fun h(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").apply {
            parts.forEach(::update)
        }.digest()
        val clientPublic = g.modPow(a, n)
        val b = BigInteger(1, bBytes)
        val k = BigInteger(1, h(pad(n), pad(g)))
        val u = BigInteger(1, h(pad(clientPublic), bBytes))
        val x = BigInteger(1, h(salt, h("Pair-Setup:249-52-118".toByteArray())))
        val s = b.subtract(k.multiply(g.modPow(x, n))).mod(n).modPow(a.add(u.multiply(x)), n)
        val key = h(pad(s))
        val hn = h(n.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it })
        val hg = h(byteArrayOf(5))
        val xor = ByteArray(64) { (hn[it].toInt() xor hg[it].toInt()).toByte() }
        val m3proof = h(xor, h("Pair-Setup".toByteArray()), salt, pad(clientPublic), bBytes, key)
        val m4 = response(exchange.handle(request(3,
            PairSetupExchange.PUBLIC_KEY to pad(clientPublic), PairSetupExchange.PROOF to m3proof,
        )))
        assertContentEquals(h(pad(clientPublic), m3proof, key), m4[PairSetupExchange.PROOF])

        val controllerSeed = ByteArray(32) { (it + 8).toByte() }
        val controllerId = "test-controller".toByteArray()
        val controllerPublic = HapCrypto.ed25519PublicKey(controllerSeed)
        val controllerX = HapCrypto.hkdf(key, "Pair-Setup-Controller-Sign-Salt", "Pair-Setup-Controller-Sign-Info")
        val signature = HapCrypto.signEd25519(controllerSeed, controllerX + controllerId + controllerPublic)
        val subTLV = Tlv8.encode(listOf(
            PairSetupExchange.IDENTIFIER to controllerId,
            PairSetupExchange.PUBLIC_KEY to controllerPublic,
            PairSetupExchange.SIGNATURE to signature,
        ))
        val encryptionKey = HapCrypto.hkdf(key, "Pair-Setup-Encrypt-Salt", "Pair-Setup-Encrypt-Info")
        val m6 = response(exchange.handle(request(5,
            PairSetupExchange.ENCRYPTED to HapCrypto.seal(encryptionKey, "PS-Msg05", subTLV),
        )))
        assertContentEquals(byteArrayOf(6), m6[PairSetupExchange.STATE])
        val decrypted = HapCrypto.open(encryptionKey, "PS-Msg06", assertNotNull(m6[PairSetupExchange.ENCRYPTED]))
        val accessoryFields = Tlv8.decode(decrypted).associate { it.first to it.second }
        val accessoryId = assertNotNull(accessoryFields[PairSetupExchange.IDENTIFIER])
        val accessoryPublic = assertNotNull(accessoryFields[PairSetupExchange.PUBLIC_KEY])
        val accessorySignature = assertNotNull(accessoryFields[PairSetupExchange.SIGNATURE])
        val accessoryX = HapCrypto.hkdf(key, "Pair-Setup-Accessory-Sign-Salt", "Pair-Setup-Accessory-Sign-Info")
        assertTrue(HapCrypto.verifyEd25519(accessoryPublic, accessoryX + accessoryId + accessoryPublic, accessorySignature))
        assertEquals("test-controller", store.controller?.identifier)
        assertTrue(store.controller?.admin == true)
    }
}
