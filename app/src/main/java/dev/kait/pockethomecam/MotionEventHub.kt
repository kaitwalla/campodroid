package dev.kait.pockethomecam

/** Thread-safe motion state fan-out; callbacks must be quick and never throw. */
internal class MotionEventHub {
    private val subscribers = mutableMapOf<Long, (Boolean) -> Unit>()
    private var nextId = 0L
    private var current = false

    @Synchronized fun state(): Boolean = current

    @Synchronized fun subscribe(callback: (Boolean) -> Unit): AutoCloseable {
        val id = ++nextId
        subscribers[id] = callback
        return AutoCloseable { synchronized(this) { subscribers.remove(id) } }
    }

    fun update(motion: Boolean) {
        val targets = synchronized(this) {
            if (motion == current) return
            current = motion
            subscribers.values.toList()
        }
        targets.forEach { subscriber -> runCatching { subscriber(motion) } }
    }
}
