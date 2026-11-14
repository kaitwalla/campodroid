package dev.kait.pockethomecam

import java.util.ArrayDeque

data class EncodedFrame(val timestampUs: Long, val flags: Int, val bytes: ByteArray)

/** Bounded in-memory encoded frame prebuffer, never uncompressed camera frames. */
class EncodedRingBuffer(private val maxDurationUs: Long = 8_000_000, private val maxBytes: Long = 16L * 1024 * 1024) {
    private val frames = ArrayDeque<EncodedFrame>()
    private var byteCount = 0L

    @Synchronized fun append(frame: EncodedFrame) {
        frames.addLast(frame)
        byteCount += frame.bytes.size
        while (frames.isNotEmpty() && (byteCount > maxBytes || frame.timestampUs - frames.first.timestampUs > maxDurationUs)) {
            byteCount -= frames.removeFirst().bytes.size
        }
    }

    @Synchronized fun snapshot(): List<EncodedFrame> = frames.toList()

    @Synchronized fun clear() {
        frames.clear()
        byteCount = 0
    }
}
