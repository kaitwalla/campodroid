package dev.kait.hap

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Tlv8Test {
    @Test fun encodesShortValue() {
        assertContentEquals(byteArrayOf(1, 2, 5, 6), Tlv8.encode(listOf(1 to byteArrayOf(5, 6))))
    }

    @Test fun roundTripsFragmentedValue() {
        val bytes = ByteArray(512) { (it % 251).toByte() }
        val parsed = Tlv8.decode(Tlv8.encode(listOf(3 to bytes)))
        assertEquals(1, parsed.size)
        assertContentEquals(bytes, parsed.single().second)
    }

    @Test fun preservesOtherTypesAndSeparators() {
        val parsed = Tlv8.decode(byteArrayOf(1, 1, 9, -1, 0, 1, 1, 10))
        assertEquals(listOf(1, 255, 1), parsed.map { it.first })
    }

    @Test fun rejectsTruncation() {
        assertFailsWith<IllegalArgumentException> { Tlv8.decode(byteArrayOf(1, 8, 4)) }
    }
}
