package dev.kait.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SrtpOutboundProtectorTest {
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun aesCmMatchesRfc3711AppendixB2Keystream() {
        val keys = SrtpKeyDerivation.SessionKeys(
            hex("2B7E151628AED2A6ABF7158809CF4F3C"), ByteArray(20),
            hex("F0F1F2F3F4F5F6F7F8F9FAFBFCFD"))
        val rtp = byteArrayOf(0x80.toByte(), 99, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0) + ByteArray(16)
        val protected = SrtpOutboundProtector(0, keys).protect(rtp)
        assertContentEquals(hex("E03EAD0935C95E80E166B16DD92B4EB4"), protected.copyOfRange(12, 28))
        assertEquals(38, protected.size) // RTP header + payload + 80-bit auth tag
    }

    @Test fun enforcesOrderAndPermitsSingleSequenceRollover() {
        val keys = SrtpKeyDerivation.SessionKeys(ByteArray(16), ByteArray(20), ByteArray(14))
        val protector = SrtpOutboundProtector(42, keys)
        fun packet(seq: Int) = byteArrayOf(0x80.toByte(), 99, (seq ushr 8).toByte(), seq.toByte(),
            0, 0, 0, 0, 0, 0, 0, 42, 7)
        protector.protect(packet(65535))
        val firstAfterWrap = protector.protect(packet(0))
        assertEquals(23, firstAfterWrap.size)
        assertFailsWith<IllegalArgumentException> { protector.protect(packet(0)) }
        assertFailsWith<IllegalArgumentException> { protector.protect(packet(2)) }
        protector.protect(packet(1))
    }

    @Test fun rejectsWrongSsrcAndUnsupportedRtpHeaders() {
        val keys = SrtpKeyDerivation.SessionKeys(ByteArray(16), ByteArray(20), ByteArray(14))
        val protector = SrtpOutboundProtector(3, keys)
        val packet = byteArrayOf(0x80.toByte(), 99, 0, 1, 0, 0, 0, 0, 0, 0, 0, 3, 9)
        assertFailsWith<IllegalArgumentException> { protector.protect(packet.copyOf().apply { this[11] = 4 }) }
        assertFailsWith<IllegalArgumentException> { protector.protect(packet.copyOf().apply { this[0] = 0x90.toByte() }) }
        protector.protect(packet)
    }
}
