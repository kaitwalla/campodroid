package dev.kait.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class H264SampleAssemblerTest {
    private fun annexB(vararg nals: ByteArray) = nals.fold(byteArrayOf()) { all, nal ->
        all + byteArrayOf(0, 0, 0, 1) + nal
    }

    @Test fun retainsCodecConfigAndPrefixesFirstIdr() {
        val assembler = H264SampleAssembler()
        val sps = byteArrayOf(0x67, 0x11)
        val pps = byteArrayOf(0x68, 0x22)
        assertNull(assembler.accept(annexB(sps, pps), 0, codecConfig = true, keyframe = false))
        val delta = requireNotNull(assembler.accept(annexB(byteArrayOf(0x41, 1)), 1_000, false, false))
        assertFalse(delta.keyframe)
        assertEquals(1, delta.nals.size)
        val idr = requireNotNull(assembler.accept(annexB(byteArrayOf(0x65, 2)), 2_000, false, true))
        assertTrue(idr.keyframe)
        assertEquals(2_000, idr.timestampUs)
        assertEquals(3, idr.nals.size)
        assertContentEquals(sps, idr.nals[0])
        assertContentEquals(pps, idr.nals[1])
        assertContentEquals(byteArrayOf(0x65, 2), idr.nals[2])
    }

    @Test fun avoidsDuplicateParameterSetsAndResetsBetweenEncoders() {
        val assembler = H264SampleAssembler()
        val sps = byteArrayOf(0x67, 4)
        val pps = byteArrayOf(0x68, 5)
        assembler.accept(annexB(sps, pps), 0, true, false)
        val embedded = requireNotNull(assembler.accept(annexB(sps, pps, byteArrayOf(0x65, 6)), 100, false, false))
        assertEquals(3, embedded.nals.size)
        assembler.reset()
        val fresh = requireNotNull(assembler.accept(annexB(byteArrayOf(0x65, 7)), 200, false, true))
        assertEquals(1, fresh.nals.size)
    }
}
