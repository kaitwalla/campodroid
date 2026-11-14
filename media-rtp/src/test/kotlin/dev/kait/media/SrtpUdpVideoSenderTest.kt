package dev.kait.media

import java.net.DatagramPacket
import java.net.DatagramSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SrtpUdpVideoSenderTest {
    @Test fun emitsEncryptedRtcpSenderReportToAdjacentPort() {
        var receivers: Pair<DatagramSocket, DatagramSocket>? = null
        repeat(64) {
            if (receivers != null) return@repeat
            val video = DatagramSocket(0)
            try {
                if (video.localPort < 65535) receivers = video to DatagramSocket(video.localPort + 1)
                else video.close()
            } catch (_: java.net.BindException) { video.close() }
        }
        val (video, rtcp) = requireNotNull(receivers)
        video.use {
            rtcp.use {
                rtcp.soTimeout = 3000
                val masterKey = ByteArray(16)
                val masterSalt = ByteArray(14)
                val session = SrtpVideoSession(5, SrtpKeyDerivation.derive(masterKey, masterSalt))
                SrtpUdpVideoSender("127.0.0.1", video.localPort, session,
                    rtcpSocket = DatagramSocket(0),
                    rtcpProtector = SrtcpOutboundProtector(5,
                        SrtpKeyDerivation.deriveRtcp(masterKey, masterSalt)),
                    ssrc = 5, reportIntervalMillis = 150).use { sender ->
                    assertTrue(sender.offer(H264AccessUnit(1_000_000, true,
                        listOf(byteArrayOf(0x65, 1, 2, 3)))))
                    val report = DatagramPacket(ByteArray(1024), 1024)
                    rtcp.receive(report)
                    assertEquals(42, report.length)
                    assertEquals(200, report.data[1].toInt() and 255)
                    assertEquals(0x80, report.data[28].toInt() and 255)
                }
            }
        }
    }

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
