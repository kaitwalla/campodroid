package dev.kait.media

/** Encoded access unit with decoder setup units included on keyframes when available. */
data class H264AccessUnit(val timestampUs: Long, val keyframe: Boolean, val nals: List<ByteArray>)

/**
 * MediaCodec typically emits SPS/PPS through BUFFER_FLAG_CODEC_CONFIG or codec-specific data.
 * A new HomeKit RTP viewer must see these before its first IDR rather than relying on capture startup.
 * Call from one encoder thread; keep instances scoped to a single encoder configuration.
 */
class H264SampleAssembler {
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    fun accept(bytes: ByteArray, timestampUs: Long, codecConfig: Boolean, keyframe: Boolean): H264AccessUnit? {
        val nals = H264NalParser.parse(bytes)
        nals.forEach { nal ->
            when (nal[0].toInt() and 31) {
                7 -> sps = nal.copyOf()
                8 -> pps = nal.copyOf()
            }
        }
        if (codecConfig) return null
        if (nals.isEmpty()) return null
        val isIdr = keyframe || nals.any { (it[0].toInt() and 31) == 5 }
        val output = mutableListOf<ByteArray>()
        if (isIdr) {
            if (nals.none { (it[0].toInt() and 31) == 7 }) sps?.let { output += it.copyOf() }
            if (nals.none { (it[0].toInt() and 31) == 8 }) pps?.let { output += it.copyOf() }
        }
        output.addAll(nals)
        return H264AccessUnit(timestampUs, isIdr, output)
    }

    fun reset() { sps = null; pps = null }
}
