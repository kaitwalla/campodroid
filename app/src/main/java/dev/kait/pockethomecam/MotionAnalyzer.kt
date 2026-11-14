package dev.kait.pockethomecam

import android.media.Image

/** CPU-cheap Y-plane detector. Process one frame at a time on a dedicated HandlerThread. */
class MotionAnalyzer(
    private val threshold: Int = 24,
    private val changedFraction: Double = 0.065,
    private val clearAfterMs: Long = 2500,
    private val sampleWidth: Int = 64,
    private val sampleHeight: Int = 36,
) {
    private val state = MotionState(threshold, changedFraction, clearAfterMs)

    /** Returns null when state is unchanged; true/false when motion starts/clears. */
    fun analyze(image: Image, nowMs: Long): Boolean? {
        val plane = image.planes[0]
        val bytes = plane.buffer
        val frame = ByteArray(sampleWidth * sampleHeight)
        var index = 0
        for (sy in 0 until sampleHeight) {
            val y = (sy * image.height / sampleHeight).coerceAtMost(image.height - 1)
            for (sx in 0 until sampleWidth) {
                val x = (sx * image.width / sampleWidth).coerceAtMost(image.width - 1)
                frame[index++] = bytes.get(y * plane.rowStride + x * plane.pixelStride)
            }
        }
        return state.process(frame, nowMs)
    }
}
