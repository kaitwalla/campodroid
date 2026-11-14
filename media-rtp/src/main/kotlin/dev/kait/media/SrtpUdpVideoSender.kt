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
) : Closeable {
    init {
        require(port in 1..65535 && queueCapacity in 1..64)
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
        while (open.get() || queue.isNotEmpty()) {
            val sample = queue.poll(100, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
            try {
                session.encode(sample).forEach { protected ->
                    socket.send(DatagramPacket(protected, protected.size, destination, destinationPort))
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
        if (Thread.currentThread() != worker) worker.join(1000)
    }
}
