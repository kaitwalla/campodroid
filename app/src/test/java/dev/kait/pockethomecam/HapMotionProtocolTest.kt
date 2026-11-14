package dev.kait.pockethomecam

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapMotionProtocolTest {
    private fun json(s: String) = s.toByteArray(Charsets.UTF_8)

    @Test fun parsesOnlyMotionSubscription() {
        assertEquals(true, HapMotionProtocol.subscriptionValue(json("""{"characteristics":[{"aid":1,"iid":8,"ev":true}]}""")))
        assertEquals(false, HapMotionProtocol.subscriptionValue(json("""{"characteristics":[{"aid":1,"iid":8,"ev":false}]}""")))
        assertNull(HapMotionProtocol.subscriptionValue(json("""{"characteristics":[{"aid":1,"iid":9,"ev":true}]}""")))
        assertNull(HapMotionProtocol.subscriptionValue(json("""{"characteristics":[{"aid":1,"iid":8,"value":true}]}""")))
    }

    @Test fun eventUsesHapEventFramingAndValue() {
        val message = String(HapMotionProtocol.eventMessage(true), Charsets.UTF_8)
        assertTrue(message.startsWith("EVENT/1.0 200 OK\r\n"))
        val parts = message.split("\r\n\r\n", limit = 2)
        val expectedLength = parts[0].lines().first { it.startsWith("Content-Length:") }
            .substringAfter(':').trim().toInt()
        assertEquals(expectedLength, parts[1].toByteArray().size)
        assertTrue(parts[1].contains("\"value\":true"))
        assertFalse(String(HapMotionProtocol.eventMessage(false)).contains("\"value\":true"))
    }
}
