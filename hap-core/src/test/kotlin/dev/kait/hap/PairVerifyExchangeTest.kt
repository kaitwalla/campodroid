package dev.kait.hap

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairVerifyExchangeTest {
    private val seed = ByteArray(32) { (it + 20).toByte() }
    private val accessory = AccessorySigningIdentity("AA:BB:CC:DD:EE:FF", ByteArray(32) { 9 })
    private val controller = TrustedController("controller-1", HapCrypto.ed25519PublicKey(seed), true)

    private fun req(state: Int, vararg items: Pair<Int, ByteArray>) =
        Tlv8.encode(listOf(PairSetupExchange.STATE to byteArrayOf(state.toByte())) + items)
    private fun parsed(bytes: ByteArray) = Tlv8.decode(bytes).associate { it.first to it.second }

    @Test fun verifiesKnownControllerAndEstablishesDirectionalKeys() {
        val exchange = PairVerifyExchange(accessory, { if (it == controller.identifier) controller else null })
        val ephemeral = HapCrypto.generateX25519()
        val m2 = parsed(exchange.handle(req(1, PairSetupExchange.PUBLIC_KEY to ephemeral.publicKey)))
        assertContentEquals(byteArrayOf(2), m2[PairSetupExchange.STATE])
        val accessoryPublic = assertNotNull(m2[PairSetupExchange.PUBLIC_KEY])
        val shared = HapCrypto.agreeX25519(ephemeral.privateKey, accessoryPublic)
        val sessionKey = HapCrypto.hkdf(shared, "Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info")
        val m2sub = parsed(HapCrypto.open(sessionKey, "PV-Msg02", assertNotNull(m2[PairSetupExchange.ENCRYPTED])))
        val accessoryId = assertNotNull(m2sub[PairSetupExchange.IDENTIFIER])
        assertTrue(HapCrypto.verifyEd25519(accessory.publicKey,
            accessoryPublic + accessoryId + ephemeral.publicKey,
            assertNotNull(m2sub[PairSetupExchange.SIGNATURE])))

        val identifier = controller.identifier.toByteArray()
        val signature = HapCrypto.signEd25519(seed, ephemeral.publicKey + identifier + accessoryPublic)
        val m3sub = Tlv8.encode(listOf(PairSetupExchange.IDENTIFIER to identifier, PairSetupExchange.SIGNATURE to signature))
        val m4 = parsed(exchange.handle(req(3,
            PairSetupExchange.ENCRYPTED to HapCrypto.seal(sessionKey, "PV-Msg03", m3sub))))
        assertContentEquals(byteArrayOf(4), m4[PairSetupExchange.STATE])
        val verified = assertNotNull(exchange.verifiedSession)
        assertEquals(controller.identifier, verified.controllerIdentifier)
        assertContentEquals(HapCrypto.hkdf(shared, "Control-Salt", "Control-Write-Encryption-Key"), verified.readKey)
        assertContentEquals(HapCrypto.hkdf(shared, "Control-Salt", "Control-Read-Encryption-Key"), verified.writeKey)
    }

    @Test fun rejectsUnknownController() {
        val exchange = PairVerifyExchange(accessory, { null })
        val client = HapCrypto.generateX25519()
        val m2 = parsed(exchange.handle(req(1, PairSetupExchange.PUBLIC_KEY to client.publicKey)))
        val serverPublic = assertNotNull(m2[PairSetupExchange.PUBLIC_KEY])
        val key = HapCrypto.hkdf(HapCrypto.agreeX25519(client.privateKey, serverPublic),
            "Pair-Verify-Encrypt-Salt", "Pair-Verify-Encrypt-Info")
        val sub = Tlv8.encode(listOf(PairSetupExchange.IDENTIFIER to "unknown".toByteArray(),
            PairSetupExchange.SIGNATURE to ByteArray(64)))
        val result = parsed(exchange.handle(req(3, PairSetupExchange.ENCRYPTED to HapCrypto.seal(key, "PV-Msg03", sub))))
        assertContentEquals(byteArrayOf(2), result[PairSetupExchange.ERROR])
        assertNull(exchange.verifiedSession)
    }
}
