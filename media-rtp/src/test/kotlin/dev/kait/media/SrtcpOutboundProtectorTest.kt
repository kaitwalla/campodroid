package dev.kait.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SrtcpOutboundProtectorTest {
    private val key = ByteArray(16) { it.toByte() }
    private val salt = ByteArray(14) { (it + 16).toByte() }

    @Test fun usesDistinctSrtcpKeysAndAuthenticatedMonotonicIndex() {
        val rtp = SrtpKeyDerivation.derive(key, salt)
        val rtcp = SrtpKeyDerivation.deriveRtcp(key, salt)
        assertFalse(rtp.encryption.contentEquals(rtcp.encryption))
        val protector = SrtcpOutboundProtector(0x01020304, rtcp)
        val packet = byteArrayOf(0x80.toByte(), 0xc9.toByte(), 0, 1, 1, 2, 3, 4) // RTCP RR
        val first = protector.protect(packet)
        val second = protector.protect(packet)
        assertEquals(packet.size + 14, first.size)
        assertContentEquals(packet, first.copyOfRange(0, packet.size)) // no RR report payload
        assertContentEquals(byteArrayOf(0x80.toByte(), 0, 0, 0), first.copyOfRange(8, 12))
        assertContentEquals(byteArrayOf(0x80.toByte(), 0, 0, 1), second.copyOfRange(8, 12))
        assertFalse(first.copyOfRange(12, 22).contentEquals(second.copyOfRange(12, 22)))
    }

    @Test fun rejectsWrongSenderAndMalformedLength() {
        val protector = SrtcpOutboundProtector(0x01020304, SrtpKeyDerivation.deriveRtcp(key, salt))
        assertFailsWith<IllegalArgumentException> {
            protector.protect(byteArrayOf(0x80.toByte(), 0xc9.toByte(), 0, 1, 1, 2, 3, 5))
        }
        assertFailsWith<IllegalArgumentException> {
            protector.protect(byteArrayOf(0x80.toByte(), 0xc9.toByte(), 0, 2, 1, 2, 3, 4))
        }
    }
}
