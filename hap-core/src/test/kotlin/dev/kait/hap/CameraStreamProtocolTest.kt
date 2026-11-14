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
        val audio = Tlv8.decode(CameraStreamProtocol.supportedAudio()).toMap()
        val codec = Tlv8.decode(audio.getValue(1)).toMap()
        assertContentEquals(byteArrayOf(2), codec.getValue(1))
        val params = Tlv8.decode(codec.getValue(2)).toMap()
        assertEquals(setOf(1, 2, 3), params.keys)
        assertContentEquals(byteArrayOf(1), params.getValue(3))
    }

    @Test fun parsesControllerEndpointAndRejectsInvalidSalt() {
        val address = fields(1 to byteArrayOf(0), 2 to "192.168.1.24".toByteArray(),
            3 to le16(50600), 4 to le16(50602))
        val video = fields(1 to byteArrayOf(0), 2 to ByteArray(16) { it.toByte() },
            3 to ByteArray(14) { it.toByte() })
        val parsed = CameraStreamProtocol.parseSetupEndpoints(fields(1 to id, 3 to address, 4 to video, 5 to video))
        assertEquals("192.168.1.24", parsed.address)
        assertEquals(50600, parsed.videoPort)
        assertEquals(50602, parsed.audioPort)
        assertContentEquals(id, parsed.sessionId)
        val response = Tlv8.decode(CameraStreamProtocol.setupResponse(parsed, "192.168.1.5",
            55000, 55002, 0x12345678, 0x12345679)).toMap()
        assertContentEquals(id, response.getValue(1))
        assertContentEquals(byteArrayOf(0), response.getValue(2))
        val responseAddress = Tlv8.decode(response.getValue(3)).toMap()
        assertContentEquals("192.168.1.5".toByteArray(), responseAddress.getValue(2))
        assertContentEquals(le16(55000), responseAddress.getValue(3))
        val responseCrypto = Tlv8.decode(response.getValue(4)).toMap()
        assertContentEquals(parsed.video.key, responseCrypto.getValue(2))
        assertContentEquals(parsed.video.salt, responseCrypto.getValue(3))
        val responseAudio = Tlv8.decode(response.getValue(5)).toMap()
        assertContentEquals(parsed.audio.key, responseAudio.getValue(2))
        assertContentEquals(parsed.audio.salt, responseAudio.getValue(3))
        assertContentEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), response.getValue(6))
        assertFailsWith<IllegalArgumentException> {
            CameraStreamProtocol.parseSetupEndpoints(fields(1 to id, 3 to address,
                4 to fields(1 to byteArrayOf(0), 2 to ByteArray(16), 3 to ByteArray(13)), 5 to video))
        }
        assertFailsWith<IllegalArgumentException> {
            CameraStreamProtocol.parseSetupEndpoints(fields(1 to id, 3 to address, 4 to video))
        }
    }

    @Test fun parsesStreamControlAndFailsClosedOnUnknownCommand() {
        val selected = CameraStreamProtocol.parseSelectedStream(
            fields(1 to fields(1 to id, 2 to byteArrayOf(1))))
        assertEquals(CameraStreamProtocol.SelectedStream.Command.START, selected.command)
        val format = fields(1 to le16(1280), 2 to le16(720), 3 to byteArrayOf(15))
        val video = fields(3 to format, 4 to fields(1 to byteArrayOf(107)))
        val negotiated = CameraStreamProtocol.parseSelectedStream(
            fields(1 to fields(1 to id, 2 to byteArrayOf(1)), 2 to video))
        assertEquals(107, negotiated.videoPayloadType)
        assertFailsWith<IllegalArgumentException> {
            CameraStreamProtocol.parseSelectedStream(fields(
                1 to fields(1 to id, 2 to byteArrayOf(1)),
                2 to fields(3 to fields(1 to le16(640), 2 to le16(480), 3 to byteArrayOf(15)))))
        }
        assertFailsWith<IllegalArgumentException> {
            CameraStreamProtocol.parseSelectedStream(fields(1 to fields(1 to id, 2 to byteArrayOf(9))))
        }
    }
}
