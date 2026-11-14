package dev.kait.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SrtpVideoSessionTest {
    @Test fun everyPacketIsAuthenticatedAndStaysUnderMtu() {
        val keys = SrtpKeyDerivation.derive(ByteArray(16) { it.toByte() }, ByteArray(14) { (it + 16).toByte() })
        val session = SrtpVideoSession(ssrc = 7, keys = keys, networkMtu = 160, startSequence = 44)
        val sample = H264AccessUnit(1_000_000, true, listOf(byteArrayOf(0x67, 1),
            byteArrayOf(0x65) + ByteArray(500) { it.toByte() }))
        val protected = session.encode(sample)
        assertTrue(protected.size > 3)
        assertTrue(protected.all { it.size <= 160 })
        assertEquals(44, ((protected[0][2].toInt() and 255) shl 8) or (protected[0][3].toInt() and 255))
        assertEquals(90_000L, (4..7).fold(0L) { v, index ->
            (v shl 8) or (protected[0][index].toLong() and 255)
        })
        assertTrue(protected.dropLast(1).all { it[1].toInt() and 0x80 == 0 })
        assertTrue(protected.last()[1].toInt() and 0x80 != 0)
        assertEquals(protected.size, protected.map { it.copyOfRange(2, 4).toList() }.toSet().size)
        val next = session.encode(H264AccessUnit(1_033_333, false, listOf(byteArrayOf(0x41, 3))))
        assertEquals(1, next.size)
        assertTrue(next[0].size > 12 + 2)
    }
}
