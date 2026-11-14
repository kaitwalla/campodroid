package dev.kait.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class H264NalParserTest {
    @Test fun parsesMixedAnnexBStartCodes() {
        val stream = byteArrayOf(0, 0, 0, 1, 0x67, 0x12, 0, 0, 1, 0x68, 0x34)
        val nals = H264NalParser.parse(stream)
        assertEquals(2, nals.size)
        assertContentEquals(byteArrayOf(0x67, 0x12), nals[0])
        assertContentEquals(byteArrayOf(0x68, 0x34), nals[1])
    }

    @Test fun parsesAvccLengthPrefixes() {
        val stream = byteArrayOf(0, 0, 0, 2, 0x65, 0x7f, 0, 0, 0, 1, 0x41)
        val nals = H264NalParser.parse(stream)
        assertEquals(2, nals.size)
        assertContentEquals(byteArrayOf(0x65, 0x7f), nals[0])
        assertContentEquals(byteArrayOf(0x41), nals[1])
    }

    @Test fun rejectsTruncatedAvcc() {
        assertFailsWith<IllegalArgumentException> { H264NalParser.parse(byteArrayOf(0, 0, 0, 10, 0x65)) }
        assertFailsWith<IllegalArgumentException> { H264NalParser.parse(byteArrayOf(0, 0, 0)) }
    }
}
