package dev.kait.pockethomecam

import dev.kait.hap.CameraStreamLifecycle
import dev.kait.hap.CameraStreamProtocol
import dev.kait.media.H264AccessUnit
import dev.kait.media.SrtpKeyDerivation
import dev.kait.media.SrtpUdpVideoSender
import dev.kait.media.SrtpVideoSession
import java.io.Closeable
import java.net.DatagramSocket
import java.security.SecureRandom

/** Experimental per-phone stream resources; HAP controller authorization is enforced by caller. */
internal class HapCameraStreamCoordinator : Closeable {
    private data class Pending(val request: CameraStreamProtocol.EndpointRequest,
                               val videoSocket: DatagramSocket, val videoRtcpSocket: DatagramSocket,
                               val audioSocket: DatagramSocket,
                               val videoSsrc: Int)
    private val state = CameraStreamLifecycle()
    private val random = SecureRandom()
    private var pending: Pending? = null
    @Volatile private var sender: SrtpUdpVideoSender? = null
    @Volatile var setupReply: ByteArray? = null
        private set
    val streaming: Boolean get() = sender != null

    @Synchronized fun setup(request: ByteArray, localAddress: String): ByteArray {
        val parsed = CameraStreamProtocol.parseSetupEndpoints(request)
        val (video, rtcp) = reserveVideoPair()
        val audio = try { DatagramSocket(0) } catch (e: Exception) {
            video.close(); rtcp.close(); throw e
        }
        val ssrc = random.nextInt()
        val audioSsrc = random.nextInt()
        return try {
            val response = CameraStreamProtocol.setupResponse(parsed, localAddress,
                video.localPort, audio.localPort, ssrc, audioSsrc)
            clear()
            pending = Pending(parsed, video, rtcp, audio, ssrc)
            state.prepare(parsed.sessionId)
            setupReply = response.copyOf()
            response
        } catch (e: Exception) {
            video.close(); rtcp.close(); audio.close(); throw e
        }
    }

    @Synchronized fun select(request: ByteArray): Boolean {
        val selected = CameraStreamProtocol.parseSelectedStream(request)
        if (selected.command == CameraStreamProtocol.SelectedStream.Command.END) {
            if (!state.command(selected)) return false
            clear()
            return true
        }
        if (selected.command != CameraStreamProtocol.SelectedStream.Command.START ||
            state.state != CameraStreamLifecycle.State.PREPARED) return false
        val prepared = pending ?: return false
        if (!prepared.request.sessionId.contentEquals(selected.sessionId)) return false
        return try {
            val derived = SrtpKeyDerivation.derive(prepared.request.video.key, prepared.request.video.salt)
            val session = SrtpVideoSession(prepared.videoSsrc, derived,
                payloadType = selected.videoPayloadType)
            val started = SrtpUdpVideoSender(prepared.request.address,
                prepared.request.videoPort, session, socket = prepared.videoSocket,
                rtcpSocket = prepared.videoRtcpSocket,
                rtcpProtector = dev.kait.media.SrtcpOutboundProtector(prepared.videoSsrc,
                    SrtpKeyDerivation.deriveRtcp(prepared.request.video.key, prepared.request.video.salt)),
                ssrc = prepared.videoSsrc)
            if (!state.command(selected)) { started.close(); return false }
            sender = started
            true
        } catch (_: Exception) { clear(); false }
    }

    fun onVideo(sample: H264AccessUnit): Boolean = sender?.offer(sample) ?: false

    @Synchronized private fun clear() {
        sender?.close(); sender = null
        setupReply = null
        pending?.videoSocket?.close()
        pending?.videoRtcpSocket?.close()
        pending?.audioSocket?.close()
        pending = null
        state.clear()
    }

    @Synchronized override fun close() = clear()

    private fun reserveVideoPair(): Pair<DatagramSocket, DatagramSocket> {
        repeat(64) {
            val video = DatagramSocket(0)
            if (video.localPort == 65535) { video.close(); return@repeat }
            try {
                return video to DatagramSocket(video.localPort + 1)
            } catch (_: java.net.BindException) {
                video.close()
            } catch (e: Exception) {
                video.close(); throw e
            }
        }
        throw IllegalStateException("Unable to reserve adjacent RTP/RTCP UDP sockets")
    }
}
