package dev.kait.hap

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AdditionalTlv8Test {
    @Test fun emptyValueRoundTrips() {
        val encoded = Tlv8.encode(listOf(7 to byteArrayOf()))
        assertContentEquals(byteArrayOf(7, 0), encoded)
        val decoded = Tlv8.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals(7, decoded.single().first)
        assertContentEquals(byteArrayOf(), decoded.single().second)
    }

    @Test fun rejectsIncompleteHeader() {
        assertFailsWith<IllegalArgumentException> { Tlv8.decode(byteArrayOf(42)) }
    }
}
