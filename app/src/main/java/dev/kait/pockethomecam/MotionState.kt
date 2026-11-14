package dev.kait.pockethomecam

import kotlin.math.abs

/** Pure Kotlin motion state machine, separate from Android image acquisition for JVM tests. */
class MotionState(
    private val threshold: Int = 24,
    private val changedFraction: Double = 0.065,
    private val clearAfterMs: Long = 2500,
) {
    private var previous: ByteArray? = null
    private var active = false
    private var lastMovementAt = 0L

    /** Returns null when unchanged; emits true on motion onset and false on clearance. */
    fun process(frame: ByteArray, nowMs: Long): Boolean? {
        val old = previous
        previous = frame.copyOf()
        if (old == null || old.size != frame.size) return null
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

    fun reset() {
        previous = null
        active = false
        lastMovementAt = 0
    }
}
