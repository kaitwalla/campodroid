package dev.kait.hap

import java.net.Inet4Address
import java.net.InetAddress

/** Protocol-only camera capabilities. Do not advertise until live RTP transport is connected. */
object CameraStreamProtocol {
    private fun one(tag: Int, value: ByteArray) = tag to value
    private fun u16(value: Int): ByteArray {
        require(value in 0..65535)
        return byteArrayOf(value.toByte(), (value ushr 8).toByte())
    }
    private fun read16(bytes: ByteArray): Int {
        require(bytes.size == 2)
        return (bytes[0].toInt() and 255) or ((bytes[1].toInt() and 255) shl 8)
    }
    private fun unique(bytes: ByteArray): Map<Int, ByteArray> {
        val entries = Tlv8.decode(bytes)
        require(entries.map { it.first }.toSet().size == entries.size) { "Duplicate camera TLV field" }
        return entries.toMap()
    }

    /** H.264 Constrained Baseline, Level 3.1, non-interleaved, 1280x720@15fps. */
    fun supportedVideo(): ByteArray {
        val parameters = Tlv8.encode(listOf(one(1, byteArrayOf(0)), one(2, byteArrayOf(0)),
            one(3, byteArrayOf(0))))
        val resolution = Tlv8.encode(listOf(one(1, u16(1280)), one(2, u16(720)),
            one(3, byteArrayOf(15))))
        val codec = Tlv8.encode(listOf(one(1, byteArrayOf(0)), one(2, parameters),
            one(3, resolution)))
        return Tlv8.encode(listOf(one(1, codec)))
    }

    /** AES_CM_128_HMAC_SHA1_80 (crypto suite 0). */
    fun supportedRtp(): ByteArray = Tlv8.encode(listOf(one(2, byteArrayOf(0))))

    data class Crypto(val key: ByteArray, val salt: ByteArray)
    data class EndpointRequest(val sessionId: ByteArray, val address: String,
                               val videoPort: Int, val audioPort: Int, val video: Crypto)

    fun parseSetupEndpoints(request: ByteArray): EndpointRequest {
        val root = unique(request)
        val session = requireNotNull(root[1])
        require(session.size == 16) { "HomeKit stream session ID must be 16 bytes" }
        val address = unique(requireNotNull(root[3]))
        require(address[1]?.contentEquals(byteArrayOf(0)) == true) { "Only IPv4 endpoints supported" }
        val host = requireNotNull(address[2]).toString(Charsets.UTF_8)
        // InetAddress.getByName can resolve arbitrary names: accept numeric IPv4 only.
        require(host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))
        require(host.split('.').all { it.toInt() in 0..255 })
        require(InetAddress.getByName(host) is Inet4Address)
        val videoPort = read16(requireNotNull(address[3]))
        val audioPort = read16(requireNotNull(address[4]))
        require(videoPort > 0 && audioPort > 0)
        val crypto = unique(requireNotNull(root[4]))
        require(crypto[1]?.contentEquals(byteArrayOf(0)) == true)
        val key = requireNotNull(crypto[2])
        val salt = requireNotNull(crypto[3])
        require(key.size == 16 && salt.size == 14)
        return EndpointRequest(session.copyOf(), host, videoPort, audioPort,
            Crypto(key.copyOf(), salt.copyOf()))
    }

    /** Construct the accessory Setup Endpoints reply after reserving UDP ports. */
    fun setupResponse(request: EndpointRequest, localAddress: String,
                      videoPort: Int, audioPort: Int, videoSsrc: Int, audioSsrc: Int): ByteArray {
        require(localAddress.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}")))
        require(localAddress.split('.').all { it.toInt() in 0..255 })
        require(videoPort in 1..65535 && audioPort in 1..65535)
        val address = Tlv8.encode(listOf(one(1, byteArrayOf(0)),
            one(2, localAddress.toByteArray(Charsets.US_ASCII)),
            one(3, u16(videoPort)), one(4, u16(audioPort))))
        val crypto = Tlv8.encode(listOf(one(1, byteArrayOf(0)),
            one(2, request.video.key), one(3, request.video.salt)))
        fun u32(value: Int) = ByteArray(4) { i -> (value ushr (i * 8)).toByte() }
        return Tlv8.encode(listOf(one(1, request.sessionId), one(2, byteArrayOf(0)),
            one(3, address), one(4, crypto), one(6, u32(videoSsrc)), one(7, u32(audioSsrc))))
    }

    data class SelectedStream(val sessionId: ByteArray, val command: Command) {
        enum class Command { END, START, RECONFIGURE }
    }

    fun parseSelectedStream(request: ByteArray): SelectedStream {
        val root = unique(request)
        val control = unique(requireNotNull(root[1]))
        val id = requireNotNull(control[1])
        require(id.size == 16)
        val command = requireNotNull(control[2])
        require(command.size == 1)
        val value = when (command[0].toInt() and 255) {
            0 -> SelectedStream.Command.END
            1 -> SelectedStream.Command.START
            2 -> SelectedStream.Command.RECONFIGURE
            else -> throw IllegalArgumentException("Unsupported stream command")
        }
        return SelectedStream(id.copyOf(), value)
    }
}
