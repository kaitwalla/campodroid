package dev.kait.hap

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails

class HapFrameCipherTest {
    private val a = ByteArray(32) { 3 }
    private val b = ByteArray(32) { 7 }

    @Test fun fragmentsAndReassemblesAcrossArbitraryTcpReads() {
        val sender = HapFrameCipher(readKey = b, writeKey = a)
        val receiver = HapFrameCipher(readKey = a, writeKey = b)
        val message = ByteArray(2600) { (it % 251).toByte() }
        val encrypted = sender.encode(message)
        val decoded = mutableListOf<ByteArray>()
        for (chunk in encrypted.asList().chunked(37)) {
            decoded += receiver.decode(chunk.toByteArray())
        }
        assertEquals(3, decoded.size)
        assertContentEquals(message, decoded.fold(ByteArray(0)) { result, bytes -> result + bytes })
    }

    @Test fun rejectsTamperingAndNeverContinuesAfterAuthenticationFailure() {
        val sender = HapFrameCipher(readKey = b, writeKey = a)
        val receiver = HapFrameCipher(readKey = a, writeKey = b)
        val frame = sender.encode("hello".toByteArray()).apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        assertFails { receiver.decode(frame) }
        assertFails { receiver.decode(sender.encode("later".toByteArray())) }
    }

    @Test fun rejectsInvalidAdvertisedLength() {
        val receiver = HapFrameCipher(readKey = a, writeKey = b)
        assertFails { receiver.decode(byteArrayOf(1, 4)) } // 1025 > HAP max 1024
    }
}
