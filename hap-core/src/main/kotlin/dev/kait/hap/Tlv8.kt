package dev.kait.hap

import java.io.ByteArrayOutputStream

/** HAP TLV8 encoder/decoder. Duplicate adjacent types are concatenated when chunks are 255 bytes. */
object Tlv8 {
    fun encode(elements: List<Pair<Int, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        for ((type, value) in elements) {
            require(type in 0..255)
            if (value.isEmpty()) {
                out.write(type)
                out.write(0)
                continue
            }
            var offset = 0
            while (offset < value.size) {
                val length = minOf(255, value.size - offset)
                out.write(type)
                out.write(length)
                out.write(value, offset, length)
                offset += length
            }
        }
        return out.toByteArray()
    }

    /** Keeps item ordering and separators; consumers must not collapse distinct repeated entries. */
    fun decode(input: ByteArray): List<Pair<Int, ByteArray>> {
        val results = mutableListOf<Pair<Int, ByteArray>>()
        var offset = 0
        while (offset < input.size) {
            require(input.size - offset >= 2) { "Truncated TLV header" }
            val type = input[offset++].toInt() and 255
            val length = input[offset++].toInt() and 255
            require(input.size - offset >= length) { "Truncated TLV value" }
            val chunk = input.copyOfRange(offset, offset + length)
            offset += length
            if (results.isNotEmpty() && results.last().first == type && results.last().second.size % 255 == 0 && results.last().second.isNotEmpty()) {
                val last = results.removeAt(results.lastIndex)
                results.add(type to (last.second + chunk))
            } else {
                results.add(type to chunk)
            }
        }
        return results
    }
}
