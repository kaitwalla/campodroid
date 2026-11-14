package dev.kait.hap

/** Thread-safe defense against repeated wrong proofs during HAP pair setup. */
class PairingThrottle(
    private val maxFailures: Int = 100,
    private val cooldownMs: Long = 30_000L,
) {
    private var failures = 0
    private var lastFailure: Long? = null

    init {
        require(maxFailures > 0)
        require(cooldownMs > 0)
    }

    @Synchronized fun canAttempt(nowMs: Long): Boolean {
        val last = lastFailure ?: return true
        return failures < maxFailures || nowMs - last >= cooldownMs
    }

    /** Call only after proof validation fails, not when a connection merely starts. */
    @Synchronized fun recordInvalidProof(nowMs: Long) {
        failures++
        if (failures >= maxFailures) lastFailure = nowMs
    }

    @Synchronized fun recordSuccess() {
        failures = 0
        lastFailure = null
    }
}
