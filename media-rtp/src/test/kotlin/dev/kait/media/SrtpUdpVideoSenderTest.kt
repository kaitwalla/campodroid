package dev.kait.media

import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SrtpUdpVideoSenderTest {
    @Test fun sendsOnlyProtectedVideoDatagramsToLoopback() {
        DatagramSocket(0).use { receiver ->
            receiver.soTimeout = 3000
            val keys = SrtpKeyDerivation.derive(ByteArray(16), ByteArray(14))
            val session = SrtpVideoSession(ssrc = 5, keys = keys, startSequence = 10)
            val sender = SrtpUdpVideoSender("127.0.0.1", receiver.localPort, session)
            sender.use {
                assertTrue(it.offer(H264AccessUnit(0, true, listOf(byteArrayOf(0x65, 1, 2, 3)))))
                val packet = DatagramPacket(ByteArray(4096), 4096)
                receiver.receive(packet)
                assertEquals(12 + 4 + 10, packet.length)
                assertEquals(0x80, packet.data[0].toInt() and 255)
                assertEquals(10, packet.data[3].toInt() and 255)
                assertFalse(packet.data.copyOfRange(12, 16).contentEquals(byteArrayOf(0x65, 1, 2, 3)))
            }
            assertFalse(sender.offer(H264AccessUnit(1, false, listOf(byteArrayOf(0x41, 1)))))
        }
    }
}
