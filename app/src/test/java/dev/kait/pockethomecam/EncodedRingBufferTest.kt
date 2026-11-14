package dev.kait.pockethomecam

import org.junit.Assert.*
import org.junit.Test

class EncodedRingBufferTest {
    @Test fun `evicts frames outside retention window`() {
        val buffer = EncodedRingBuffer(maxDurationUs = 1000, maxBytes = 1000)
        buffer.append(EncodedFrame(0, 0, byteArrayOf(1)))
        buffer.append(EncodedFrame(500, 0, byteArrayOf(2)))
        buffer.append(EncodedFrame(1500, 0, byteArrayOf(3)))
        assertEquals(listOf(500L, 1500L), buffer.snapshot().map { it.timestampUs })
    }

    @Test fun `enforces byte cap and clears`() {
        val buffer = EncodedRingBuffer(maxDurationUs = 10_000, maxBytes = 4)
        buffer.append(EncodedFrame(0, 0, byteArrayOf(1, 2, 3)))
        buffer.append(EncodedFrame(1, 0, byteArrayOf(4, 5, 6)))
        assertEquals(1, buffer.snapshot().size)
        assertEquals(1L, buffer.snapshot().single().timestampUs)
        buffer.clear()
        assertTrue(buffer.snapshot().isEmpty())
    }
}
