package dev.kait.hap

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PairingThrottleTest {
    @Test fun locksAfterRepeatedBadProofs() {
        val throttle = PairingThrottle(maxFailures = 3, cooldownMs = 30_000)
        assertTrue(throttle.canAttempt(100))
        throttle.recordInvalidProof(100)
        throttle.recordInvalidProof(200)
        assertTrue(throttle.canAttempt(201))
        throttle.recordInvalidProof(300)
        assertFalse(throttle.canAttempt(301))
        assertTrue(throttle.canAttempt(30_300))
    }

    @Test fun successfulPairingResetsFailureCounter() {
        val throttle = PairingThrottle(maxFailures = 1)
        throttle.recordInvalidProof(100)
        assertFalse(throttle.canAttempt(200))
        throttle.recordSuccess()
        assertTrue(throttle.canAttempt(201))
    }
}
