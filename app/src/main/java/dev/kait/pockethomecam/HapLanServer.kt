package dev.kait.pockethomecam

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dev.kait.hap.HapFrameCipher
import dev.kait.hap.PairSetupExchange
import dev.kait.hap.PairVerifyExchange
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.MessageDigest
import kotlin.concurrent.thread

/** Minimal local HAP transport. No camera stream is advertised until implemented. */
internal class HapLanServer(private val context: Context, private val identity: HapIdentityRepository,
                   private val motionEvents: MotionEventHub = MotionEventHub(),
                   private val experimentalCamera: Boolean = false,
                   private val onCameraStreamStarted: () -> Unit = {}) {
    private val cameraStreams = HapCameraStreamCoordinator()
    private var socket: ServerSocket? = null
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var registration: NsdManager.RegistrationListener? = null
    @Volatile private var running = false
    private val pairingThrottle = dev.kait.hap.PairingThrottle()

    fun motionChanged(active: Boolean) = motionEvents.update(active)
    fun video(sample: dev.kait.media.H264AccessUnit) { if (experimentalCamera) cameraStreams.onVideo(sample) }

    fun start() {
        check(!running)
        val listening = ServerSocket(0)
        socket = listening
        running = true
        advertise(listening.localPort)
        thread(name = "hap-listener") {
            while (running) {
                try {
                    val client = listening.accept()
                    thread(name = "hap-client") { handleClient(client) }
                } catch (e: SocketException) {
                    if (running) Log.e(TAG, "Accept failed", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Accept failed", e)
                }
            }
        }
    }

    fun stop() {
        running = false
        cameraStreams.close()
        socket?.close()
        socket = null
        registration?.let { runCatching { nsd.unregisterService(it) } }
        registration = null
    }

    private fun advertise(port: Int) {
        val setup = identity.setupIdentity()
        val device = identity.signingIdentity()
        val hash = MessageDigest.getInstance("SHA-512")
            .digest((setup.setupId + device.identifier).toByteArray())
            .copyOfRange(0, 4)
        val service = NsdServiceInfo().apply {
            serviceName = "CamPodroid"
            serviceType = "_hap._tcp."
            setPort(port)
            setAttribute("c#", HapDiscoveryMetadata.configurationNumber(experimentalCamera))
            setAttribute("ff", "0")
            setAttribute("id", device.identifier)
            setAttribute("md", "CamPodroid")
            setAttribute("pv", "1.1")
            setAttribute("s#", "1")
            setAttribute("sf", if (identity.isPaired()) "0" else "1")
            setAttribute("ci", HapDiscoveryMetadata.category(experimentalCamera))
            setAttribute("sh", android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP))
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(service: NsdServiceInfo) { Log.i(TAG, "HAP mDNS registered") }
            override fun onRegistrationFailed(service: NsdServiceInfo, errorCode: Int) { Log.e(TAG, "HAP mDNS error $errorCode") }
            override fun onServiceUnregistered(service: NsdServiceInfo) {}
            override fun onUnregistrationFailed(service: NsdServiceInfo, errorCode: Int) {}
        }
        registration = listener
        nsd.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private data class Request(val method: String, val path: String, val body: ByteArray)

    private fun parseHttp(pending: ByteArray): Pair<Request, Int>? {
        val boundary = "\r\n\r\n".toByteArray()
        val headerEnd = pending.indices.firstOrNull { index ->
            index + boundary.size <= pending.size && pending.copyOfRange(index, index + boundary.size).contentEquals(boundary)
        } ?: return null
        require(headerEnd < 8192)
        val lines = String(pending, 0, headerEnd, Charsets.US_ASCII).split("\r\n")
        val parts = lines.first().split(' ')
        require(parts.size == 3 && parts[2].startsWith("HTTP/1."))
        val length = lines.drop(1).firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toInt() ?: 0
        require(length in 0..16384)
        val consumed = headerEnd + 4 + length
        if (pending.size < consumed) return null
        return Request(parts[0], parts[1], pending.copyOfRange(headerEnd + 4, consumed)) to consumed
    }

    private fun response(code: Int, body: ByteArray, contentType: String): ByteArray {
        val status = when (code) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            470 -> "Connection Authorization Required"
            else -> "Error"
        }
        return ("HTTP/1.1 $code $status\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: keep-alive\r\n\r\n")
            .toByteArray(Charsets.US_ASCII) + body
    }

    private fun handleClient(client: Socket) {
        client.use { conn ->
            conn.soTimeout = 90_000
            val setup = PairSetupExchange(identity.setupIdentity().code, identity.signingIdentity(), identity,
                pairingThrottle)
            val verify = PairVerifyExchange(identity.signingIdentity(), identity::findController)
            var cipher: HapFrameCipher? = null
            val writeLock = Any()
            var motionSubscription: AutoCloseable? = null
            var pending = ByteArray(0)
            val input = conn.getInputStream()
            val output = conn.getOutputStream()
            val readBuffer = ByteArray(4096)
            try {
                while (running && !conn.isClosed) {
                    val count = input.read(readBuffer)
                    if (count < 0) break
                    val fragments = cipher?.decode(readBuffer.copyOf(count)) ?: listOf(readBuffer.copyOf(count))
                    for (fragment in fragments) {
                        pending += fragment
                        require(pending.size <= 32768)
                        while (true) {
                            val (request, consumed) = parseHttp(pending) ?: break
                            pending = pending.copyOfRange(consumed, pending.size)
                            val authenticated = cipher != null
                            val answer: ByteArray = when {
                                request.method == "POST" && request.path == "/pair-setup" && !authenticated ->
                                    response(200, setup.handle(request.body), "application/pairing+tlv8")
                                request.method == "POST" && request.path == "/pair-verify" ->
                                    response(200, verify.handle(request.body), "application/pairing+tlv8")
                                authenticated && request.method == "GET" && request.path == "/accessories" ->
                                    response(200, HapAccessoryDatabase.accessories(
                                        identity.signingIdentity().identifier, motionEvents.state(),
                                        experimentalCamera, cameraStreams.streaming), "application/hap+json")
                                authenticated && request.method == "GET" && request.path.startsWith("/characteristics?") -> {
                                    val body = if (experimentalCamera) HapCameraCharacteristics.read(
                                        request.path, cameraStreams.setupReply, cameraStreams.streaming,
                                        motionEvents.state()) else HapAccessoryDatabase.motionRead(
                                            request.path, motionEvents.state())
                                    if (body == null) response(400, byteArrayOf(), "application/hap+json")
                                    else response(200, body, "application/hap+json")
                                }
                                authenticated && request.method == "PUT" && request.path == "/characteristics" -> {
                                    val cameraWrite = if (experimentalCamera)
                                        HapCameraCharacteristics.parseWrite(request.body) else null
                                    val enabled = if (cameraWrite == null)
                                        HapMotionProtocol.subscriptionValue(request.body) else null
                                    if (cameraWrite != null) {
                                        val accepted = runCatching {
                                            when (cameraWrite.iid) {
                                                24 -> { cameraStreams.setup(cameraWrite.payload,
                                                    conn.localAddress.hostAddress ?: "127.0.0.1"); true }
                                                25 -> cameraStreams.select(cameraWrite.payload).also {
                                                    if (it && cameraStreams.streaming) onCameraStreamStarted()
                                                }
                                                else -> false
                                            }
                                        }.getOrDefault(false)
                                        response(if (accepted) 204 else 400, byteArrayOf(), "application/hap+json")
                                    } else if (enabled == null) response(400, byteArrayOf(), "application/hap+json")
                                    else {
                                        motionSubscription?.close()
                                        motionSubscription = if (enabled) motionEvents.subscribe { active ->
                                            val currentCipher = cipher ?: return@subscribe
                                            try {
                                                synchronized(writeLock) {
                                                    output.write(currentCipher.encode(HapMotionProtocol.eventMessage(active)))
                                                    output.flush()
                                                }
                                            } catch (_: Exception) { /* Connection cleanup follows on next I/O. */ }
                                        } else null
                                        response(204, byteArrayOf(), "application/hap+json")
                                    }
                                }
                                authenticated && request.method == "POST" && request.path == "/identify" ->
                                    response(204, byteArrayOf(), "application/hap+json")
                                else -> response(470, byteArrayOf(), "text/plain")
                            }
                            val current = cipher
                            synchronized(writeLock) {
                                output.write(if (current == null) answer else current.encode(answer))
                                output.flush()
                            }
                            // Pair-Verify M4 is plaintext; subsequent requests use authenticated framing.
                            if (current == null && verify.verifiedSession != null) {
                                val session = requireNotNull(verify.verifiedSession)
                                cipher = HapFrameCipher(session.readKey, session.writeKey)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "HAP connection closed: ${e.javaClass.simpleName}")
            } finally {
                motionSubscription?.close()
            }
        }
    }

    companion object { private const val TAG = "CamPodroidHAP" }
}
