package dev.kait.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class SrtpKeyDerivationTest {
    private fun hex(value: String): ByteArray = value.chunked(2)
        .map { it.toInt(16).toByte() }.toByteArray()

    @Test fun matchesRfc3711AppendixB3KeyDerivation() {
        val derived = SrtpKeyDerivation.derive(
            hex("E1F97A0D3E018BE0D64FA32C06DE4139"),
            hex("0EC675AD498AFEEBB6960B3AABE6"),
        )
        assertContentEquals(hex("C61E7A93744F39EE10734AFE3FF7A087"), derived.encryption)
        assertContentEquals(hex("CEBE321F6FF7716B6FD4AB49AF256A156D38BAA4"), derived.authentication)
        assertContentEquals(hex("30CBBC08863D8C85D49DB34A9AE1"), derived.salt)
    }

    @Test fun rejectsUnexpectedMasterKeySizes() {
        assertFailsWith<IllegalArgumentException> { SrtpKeyDerivation.derive(ByteArray(15), ByteArray(14)) }
        assertFailsWith<IllegalArgumentException> { SrtpKeyDerivation.derive(ByteArray(16), ByteArray(13)) }
    }
}
