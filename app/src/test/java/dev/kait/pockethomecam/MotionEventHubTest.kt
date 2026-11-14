package dev.kait.pockethomecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionEventHubTest {
    @Test fun fanOutOnlyTransitionsAndUnsubscribe() {
        val hub = MotionEventHub()
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()
        val one = hub.subscribe(first::add)
        hub.subscribe(second::add)
        hub.update(true)
        hub.update(true)
        assertTrue(hub.state())
        one.close()
        hub.update(false)
        assertFalse(hub.state())
        assertEquals(listOf(true), first)
        assertEquals(listOf(true, false), second)
    }

    @Test fun subscriberFailureDoesNotBlockOthers() {
        val hub = MotionEventHub()
        var received = false
        hub.subscribe { error("broken socket") }
        hub.subscribe { received = it }
        hub.update(true)
        assertTrue(received)
    }
}
