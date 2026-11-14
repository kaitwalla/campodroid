package dev.kait.media

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Bounded, asynchronous SRTP datagram sender. It sends only protected packets.
 * A HAP stream manager must authenticate/validate the peer address and negotiate keys first.
 * No retransmission, RTCP, bitrate adaptation or keyframe recovery is implemented here.
 */
class SrtpUdpVideoSender(
    host: String,
    port: Int,
    private val session: SrtpVideoSession,
    queueCapacity: Int = 8,
    private val socket: DatagramSocket = DatagramSocket(),
    private val rtcpSocket: DatagramSocket? = null,
    private val rtcpProtector: SrtcpOutboundProtector? = null,
    private val ssrc: Int = 0,
    reportIntervalMillis: Long = 5000,
) : Closeable {
    init {
        require(port in 1..65535 && queueCapacity in 1..64)
        require(reportIntervalMillis in 100..60000)
        require((rtcpSocket == null) == (rtcpProtector == null))
        require(rtcpSocket == null || port < 65535)
        require(host.matches(Regex("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))) {
            "Media destination must be numeric IPv4"
        }
        require(host.split('.').all { it.toInt() in 0..255 })
    }
    private val destination = InetAddress.getByName(host).also {
        require(it is Inet4Address) { "Only numeric/IPv4 media peers supported" }
    }
    private val destinationPort = port
    private val queue = ArrayBlockingQueue<H264AccessUnit>(queueCapacity)
    private val open = AtomicBoolean(true)
    private val worker = thread(name = "campodroid-srtp-video", isDaemon = true) {
        var packetCount = 0L
        var octetCount = 0L
        var rtpTimestamp = 0L
        var lastReport = System.currentTimeMillis()
        while (open.get() || queue.isNotEmpty()) {
            try {
                val sample = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (sample != null) {
                    session.encode(sample).forEach { protected ->
                        socket.send(DatagramPacket(protected, protected.size, destination, destinationPort))
                        packetCount++
                        octetCount += protected.size - 12 - 10 // RTP payload, excluding SRTP auth tag
                        rtpTimestamp = (4..7).fold(0L) { value, i ->
                            (value shl 8) or (protected[i].toLong() and 255)
                        }
                    }
                }
                val now = System.currentTimeMillis()
                if (rtcpSocket != null && rtcpProtector != null && packetCount > 0 &&
                    now - lastReport >= reportIntervalMillis) {
                    val report = rtcpProtector.protect(RtcpSenderReport.build(
                        ssrc, now, rtpTimestamp, packetCount, octetCount))
                    rtcpSocket.send(DatagramPacket(report, report.size, destination, destinationPort + 1))
                    lastReport = now
                }
            } catch (_: Exception) {
                // A session manager can tear down and replace failed senders.
                open.set(false)
                queue.clear()
                break
            }
        }
    }

    /** Nonblocking backpressure: a full queue drops the whole access unit. */
    fun offer(sample: H264AccessUnit): Boolean = open.get() && queue.offer(sample)

    override fun close() {
        open.set(false)
        queue.clear()
        socket.close()
        rtcpSocket?.close()
        if (Thread.currentThread() != worker) worker.join(1000)
    }
}
