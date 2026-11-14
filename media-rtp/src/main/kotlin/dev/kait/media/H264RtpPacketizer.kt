package dev.kait.media

/** RFC 6184 single NAL and FU-A packetizer. SRTP protection is a separate layer. */
class H264RtpPacketizer(val ssrc: Int, private val payloadType: Int = 99,
                        private val mtu: Int = 1200, startSequence: Int = 0) {
    init { require(payloadType in 0..127 && mtu >= 15 && mtu <= 65507) }
    private var sequence = startSequence and 0xffff

    @Synchronized fun packetize(nals: List<ByteArray>, timestampUs: Long): List<ByteArray> {
        require(timestampUs >= 0)
        val packets = mutableListOf<ByteArray>()
        val timestamp = timestampUs * 90 / 1000 // MediaCodec microseconds -> RTP 90 kHz.
        nals.forEachIndexed { nalIndex, nal ->
            require(nal.isNotEmpty())
            val type = nal[0].toInt() and 31
            require(type in 1..23) { "Unsupported H.264 NAL type $type" }
            val lastNal = nalIndex == nals.lastIndex
            if (nal.size <= mtu - 12) {
                packets += packet(nal, timestamp, lastNal)
            } else {
                val chunkSize = mtu - 14
                var offset = 1
                while (offset < nal.size) {
                    val remaining = nal.size - offset
                    val length = minOf(chunkSize, remaining)
                    val start = offset == 1
                    val end = length == remaining
                    val payload = ByteArray(length + 2)
                    payload[0] = ((nal[0].toInt() and 0xe0) or 28).toByte()
                    payload[1] = (type or (if (start) 0x80 else 0) or (if (end) 0x40 else 0)).toByte()
                    nal.copyInto(payload, 2, offset, offset + length)
                    packets += packet(payload, timestamp, lastNal && end)
                    offset += length
                }
            }
        }
        return packets
    }

    private fun packet(payload: ByteArray, timestamp: Long, marker: Boolean): ByteArray =
        ByteArray(12 + payload.size).also { bytes ->
            bytes[0] = 0x80.toByte()
            bytes[1] = (payloadType or if (marker) 0x80 else 0).toByte()
            val seq = sequence++ and 0xffff
            put16(bytes, 2, seq)
            put32(bytes, 4, timestamp)
            put32(bytes, 8, ssrc.toLong() and 0xffffffffL)
            payload.copyInto(bytes, 12)
        }

    private fun put16(out: ByteArray, at: Int, value: Int) {
        out[at] = (value ushr 8).toByte(); out[at + 1] = value.toByte()
    }

    private fun put32(out: ByteArray, at: Int, value: Long) {
        for (i in 0..3) out[at + i] = (value ushr ((3 - i) * 8)).toByte()
    }
}
