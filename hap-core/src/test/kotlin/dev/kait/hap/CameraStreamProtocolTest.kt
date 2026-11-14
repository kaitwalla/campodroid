package dev.kait.hap

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CameraStreamProtocolTest {
    private fun fields(vararg pairs: Pair<Int, ByteArray>) = Tlv8.encode(pairs.toList())
    private fun le16(n: Int) = byteArrayOf(n.toByte(), (n ushr 8).toByte())
    private val id = ByteArray(16) { it.toByte() }

    @Test fun videoAndRtpCapabilitiesHaveExpectedNestedTags() {
        val video = Tlv8.decode(CameraStreamProtocol.supportedVideo()).single()
        assertEquals(1, video.first)
        val config = Tlv8.decode(video.second).toMap()
        assertContentEquals(byteArrayOf(0), config.getValue(1))
        val attrs = Tlv8.decode(config.getValue(3)).toMap()
        assertContentEquals(le16(1280), attrs.getValue(1))
        assertContentEquals(le16(720), attrs.getValue(2))
        assertContentEquals(byteArrayOf(15), attrs.getValue(3))
        assertContentEquals(byteArrayOf(0), Tlv8.decode(CameraStreamProtocol.supportedRtp()).single().second)
    }

    @Test fun parsesControllerEndpointAndRejectsInvalidSalt() {
        val address = fields(1 to byteArrayOf(0), 2 to "192.168.1.24".toByteArray(),
            3 to le16(50600), 4 to le16(50602))
        val video = fields(1 to byteArrayOf(0), 2 to ByteArray(16) { it.toByte() },
            3 to ByteArray(14) { it.toByte() })
        val parsed = CameraStreamProtocol.parseSetupEndpoints(fields(1 to id, 3 to address, 4 to video))
        assertEquals("192.168.1.24", parsed.address)
        assertEquals(50600, parsed.videoPort)
        assertEquals(50602, parsed.audioPort)
        assertContentEquals(id, parsed.sessionId)
        assertFailsWith<IllegalArgumentException> {
            CameraStreamProtocol.parseSetupEndpoints(fields(1 to id, 3 to address,
                4 to fields(1 to byteArrayOf(0), 2 to ByteArray(16), 3 to ByteArray(13))))
        }
    }

    @Test fun parsesStreamControlAndFailsClosedOnUnknownCommand() {
        val selected = CameraStreamProtocol.parseSelectedStream(
            fields(1 to fields(1 to id, 2 to byteArrayOf(1))))
        assertEquals(CameraStreamProtocol.SelectedStream.Command.START, selected.command)
        assertFailsWith<IllegalArgumentException> {
            CameraStreamProtocol.parseSelectedStream(fields(1 to fields(1 to id, 2 to byteArrayOf(9))))
        }
    }
}
