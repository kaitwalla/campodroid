package dev.kait.hap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraStreamLifecycleTest {
    private val a = ByteArray(16) { it.toByte() }
    private val b = ByteArray(16) { (it + 1).toByte() }
    private fun command(id: ByteArray, action: CameraStreamProtocol.SelectedStream.Command) =
        CameraStreamProtocol.SelectedStream(id, action)

    @Test fun startsOnlyPreparedMatchingSessionAndStopsCleanly() {
        val lifecycle = CameraStreamLifecycle()
        assertFalse(lifecycle.command(command(a, CameraStreamProtocol.SelectedStream.Command.START)))
        lifecycle.prepare(a)
        assertFalse(lifecycle.command(command(b, CameraStreamProtocol.SelectedStream.Command.START)))
        assertFalse(lifecycle.command(command(a, CameraStreamProtocol.SelectedStream.Command.RECONFIGURE)))
        assertTrue(lifecycle.command(command(a, CameraStreamProtocol.SelectedStream.Command.START)))
        assertEquals(CameraStreamLifecycle.State.STREAMING, lifecycle.state)
        assertFalse(lifecycle.command(command(a, CameraStreamProtocol.SelectedStream.Command.START)))
        assertTrue(lifecycle.command(command(a, CameraStreamProtocol.SelectedStream.Command.END)))
        assertEquals(CameraStreamLifecycle.State.IDLE, lifecycle.state)
        assertFalse(lifecycle.command(command(a, CameraStreamProtocol.SelectedStream.Command.END)))
    }

    @Test fun replacementInvalidatesPriorSession() {
        val lifecycle = CameraStreamLifecycle()
        lifecycle.prepare(a)
        lifecycle.prepare(b)
        assertFalse(lifecycle.command(command(a, CameraStreamProtocol.SelectedStream.Command.START)))
        assertTrue(lifecycle.command(command(b, CameraStreamProtocol.SelectedStream.Command.START)))
        lifecycle.clear()
        assertEquals(CameraStreamLifecycle.State.IDLE, lifecycle.state)
    }
}
