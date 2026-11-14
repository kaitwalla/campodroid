package dev.kait.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class H264RtpPacketizerTest {
    private fun u8(b: Byte) = b.toInt() and 255
    private fun seq(packet: ByteArray) = u8(packet[2]) * 256 + u8(packet[3])
    private fun timestamp(packet: ByteArray) = (4..7).fold(0L) { acc, i -> (acc shl 8) or u8(packet[i]).toLong() }

    @Test fun singleNalUnitsShareTimestampAndOnlyLastHasMarker() {
        val p = H264RtpPacketizer(0x12345678, startSequence = 65535)
        val packets = p.packetize(listOf(byteArrayOf(0x67, 1), byteArrayOf(0x65, 2)), 1_000_000)
        assertEquals(2, packets.size)
        assertEquals(65535, seq(packets[0]))
        assertEquals(0, seq(packets[1]))
        assertEquals(90_000, timestamp(packets[0]))
        assertEquals(timestamp(packets[0]), timestamp(packets[1]))
        assertFalse(u8(packets[0][1]) and 0x80 != 0)
        assertTrue(u8(packets[1][1]) and 0x80 != 0)
        assertContentEquals(byteArrayOf(0x65, 2), packets[1].copyOfRange(12, packets[1].size))
    }

    @Test fun fragmentsLargeNalAndReassemblesExactly() {
        val original = byteArrayOf(0x65) + ByteArray(127) { it.toByte() }
        val packets = H264RtpPacketizer(1, mtu = 42).packetize(listOf(original), 2_000_000)
        assertTrue(packets.size > 2)
        assertEquals(28, u8(packets[0][12]) and 31)
        assertTrue(u8(packets.first()[13]) and 0x80 != 0)
        assertTrue(u8(packets.last()[13]) and 0x40 != 0)
        assertTrue(u8(packets.last()[1]) and 0x80 != 0)
        assertTrue(packets.dropLast(1).all { u8(it[1]) and 0x80 == 0 })
        assertTrue(packets.all { it.size <= 42 })
        val restored = byteArrayOf(original[0]) + packets.flatMap { packet ->
            packet.copyOfRange(14, packet.size).toList()
        }.toByteArray()
        assertContentEquals(original, restored)
    }

    @Test fun emptyAccessUnitEmitsNothing() {
        assertTrue(H264RtpPacketizer(0).packetize(emptyList(), 0).isEmpty())
    }
}
