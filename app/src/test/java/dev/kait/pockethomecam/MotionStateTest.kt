package dev.kait.pockethomecam

import org.junit.Assert.*
import org.junit.Test

class MotionStateTest {
    private val calm = ByteArray(100)
    private val changed = ByteArray(100) { 90.toByte() }

    @Test fun `reports onset once and clears after calm interval`() {
        val state = MotionState(clearAfterMs = 1000)
        assertNull(state.process(calm, 0))
        assertEquals(true, state.process(changed, 100))
        assertNull(state.process(calm, 200)) // movement extends active window
        assertNull(state.process(calm, 1199))
        assertEquals(false, state.process(calm, 1200))
    }

    @Test fun `noise below threshold does not trigger`() {
        val state = MotionState()
        assertNull(state.process(calm, 0))
        assertNull(state.process(ByteArray(100) { 10 }, 100))
    }

    @Test fun `too few changed pixels do not trigger`() {
        val state = MotionState(changedFraction = 0.10)
        assertNull(state.process(calm, 0))
        assertNull(state.process(ByteArray(100) { if (it < 9) 90.toByte() else 0 }, 100))
    }

    @Test fun `frame geometry changes reset baseline without false events`() {
        val state = MotionState()
        assertNull(state.process(calm, 0))
        assertNull(state.process(ByteArray(200) { 90.toByte() }, 100))
        assertNull(state.process(ByteArray(200) { 90.toByte() }, 200))
    }

    @Test fun `explicit reset clears prior state`() {
        val state = MotionState()
        state.process(calm, 0)
        assertEquals(true, state.process(changed, 100))
        state.reset()
        assertNull(state.process(changed, 200))
    }
}
