package dev.kait.pockethomecam

import android.media.Image
import kotlin.math.abs

/** CPU-cheap Y-plane detector. Process one frame at a time on a dedicated HandlerThread. */
class MotionAnalyzer(
    private val threshold: Int = 24,
    private val changedFraction: Double = 0.065,
    private val clearAfterMs: Long = 2500,
    private val sampleWidth: Int = 64,
    private val sampleHeight: Int = 36,
) {
    private var previous: ByteArray? = null
    private var active = false
    private var lastMovementAt = 0L

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
        val old = previous
        previous = frame
        if (old == null) return null
        var changed = 0
        for (i in frame.indices) {
            if (abs((frame[i].toInt() and 255) - (old[i].toInt() and 255)) >= threshold) changed++
        }
        if (changed.toDouble() / frame.size >= changedFraction) {
            lastMovementAt = nowMs
            if (!active) {
                active = true
                return true
            }
        } else if (active && nowMs - lastMovementAt >= clearAfterMs) {
            active = false
            return false
        }
        return null
    }
}
