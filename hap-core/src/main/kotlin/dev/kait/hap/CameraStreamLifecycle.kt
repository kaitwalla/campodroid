package dev.kait.hap

import java.security.MessageDigest

/** Single-camera, one-session-at-a-time state machine; transport and resource cleanup live outside. */
class CameraStreamLifecycle {
    enum class State { IDLE, PREPARED, STREAMING }
    private var sessionId: ByteArray? = null
    var state = State.IDLE
        private set

    /** Replaces a prepared session; caller must stop/close any previous transport. */
    @Synchronized fun prepare(id: ByteArray) {
        require(id.size == 16)
        sessionId = id.copyOf()
        state = State.PREPARED
    }

    /** Rejects commands for stale/unknown sessions and unsupported reconfigure. */
    @Synchronized fun command(command: CameraStreamProtocol.SelectedStream): Boolean {
        val activeId = sessionId ?: return false
        if (!MessageDigest.isEqual(activeId, command.sessionId)) return false
        return when (command.command) {
            CameraStreamProtocol.SelectedStream.Command.START -> {
                if (state != State.PREPARED) false else { state = State.STREAMING; true }
            }
            CameraStreamProtocol.SelectedStream.Command.END -> {
                if (state == State.IDLE) false else { clear(); true }
            }
            CameraStreamProtocol.SelectedStream.Command.RECONFIGURE -> false
        }
    }

    @Synchronized fun clear() {
        sessionId?.fill(0)
        sessionId = null
        state = State.IDLE
    }
}
