package dev.kait.pockethomecam

import dev.kait.hap.Tlv8
import dev.kait.media.H264AccessUnit
import java.net.DatagramPacket
import java.net.DatagramSocket
import org.junit.Assert.*
import org.junit.Test

class HapCameraStreamCoordinatorTest {
    private fun tlv(vararg fields: Pair<Int, ByteArray>) = Tlv8.encode(fields.toList())
    private fun le16(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte())
    private val id = ByteArray(16) { it.toByte() }

    @Test fun startsSrtpOnlyForMatchingSessionAndStopsSending() {
        DatagramSocket(0).use { receiver ->
            receiver.soTimeout = 3000
            val address = tlv(1 to byteArrayOf(0), 2 to "127.0.0.1".toByteArray(),
                3 to le16(receiver.localPort), 4 to le16(receiver.localPort))
            val crypto = tlv(1 to byteArrayOf(0), 2 to ByteArray(16), 3 to ByteArray(14))
            HapCameraStreamCoordinator().use { coordinator ->
                assertFalse(coordinator.onVideo(H264AccessUnit(0, true, listOf(byteArrayOf(0x65, 1)))))
                val reply = coordinator.setup(tlv(1 to id, 3 to address, 4 to crypto), "127.0.0.1")
                assertTrue(Tlv8.decode(reply).toMap().containsKey(6))
                val wrong = tlv(1 to tlv(1 to ByteArray(16), 2 to byteArrayOf(1)))
                assertFalse(coordinator.select(wrong))
                val start = tlv(1 to tlv(1 to id, 2 to byteArrayOf(1)))
                assertTrue(coordinator.select(start))
                assertTrue(coordinator.onVideo(H264AccessUnit(0, true, listOf(byteArrayOf(0x65, 1)))))
                val packet = DatagramPacket(ByteArray(4096), 4096)
                receiver.receive(packet)
                assertTrue(packet.length > 12 + 2)
                assertFalse(coordinator.select(start))
                assertTrue(coordinator.select(tlv(1 to tlv(1 to id, 2 to byteArrayOf(0)))))
                assertFalse(coordinator.onVideo(H264AccessUnit(1, false, listOf(byteArrayOf(0x41, 1)))))
            }
        }
    }
}
