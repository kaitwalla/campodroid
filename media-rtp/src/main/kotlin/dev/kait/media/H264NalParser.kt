package dev.kait.media

/** Converts MediaCodec H.264 access units (Annex B or AVCC) into raw NAL units. */
object H264NalParser {
    fun parse(bytes: ByteArray): List<ByteArray> {
        if (bytes.isEmpty()) return emptyList()
        if (startCodeAt(bytes, 0) > 0) return parseAnnexB(bytes)
        return parseAvcc(bytes)
    }

    private fun startCodeAt(data: ByteArray, index: Int): Int {
        if (index + 2 >= data.size || data[index] != 0.toByte() || data[index + 1] != 0.toByte()) return 0
        if (data[index + 2] == 1.toByte()) return 3
        return if (index + 3 < data.size && data[index + 2] == 0.toByte() && data[index + 3] == 1.toByte()) 4 else 0
    }

    private fun parseAnnexB(data: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var current = startCodeAt(data, 0)
        require(current > 0)
        while (current < data.size) {
            var next = current
            while (next < data.size && startCodeAt(data, next) == 0) next++
            var end = next
            if (next == data.size) while (end > current && data[end - 1] == 0.toByte()) end--
            if (end > current) result += data.copyOfRange(current, end)
            current = next + startCodeAt(data, next)
        }
        return result
    }

    private fun parseAvcc(data: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < data.size) {
            require(data.size - offset >= 4) { "Truncated AVCC length" }
            val length = ((data[offset].toLong() and 255) shl 24) or
                ((data[offset + 1].toLong() and 255) shl 16) or
                ((data[offset + 2].toLong() and 255) shl 8) or (data[offset + 3].toLong() and 255)
            offset += 4
            require(length in 1..(data.size - offset).toLong()) { "Invalid AVCC NAL size" }
            result += data.copyOfRange(offset, offset + length.toInt())
            offset += length.toInt()
        }
        return result
    }
}
