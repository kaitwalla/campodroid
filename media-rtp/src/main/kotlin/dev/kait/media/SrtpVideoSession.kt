package dev.kait.media

/**
 * In-memory RFC 6184 -> RFC 3711 video pipeline. No networking or key negotiation.
 * Construct one instance for each independently negotiated HomeKit stream, with new keys/SSRC.
 */
class SrtpVideoSession(
    ssrc: Int,
    keys: SrtpKeyDerivation.SessionKeys,
    payloadType: Int = 99,
    networkMtu: Int = 1200,
    startSequence: Int = 0,
) {
    init { require(networkMtu in 64..1400) }
    private val packetizer = H264RtpPacketizer(ssrc, payloadType, networkMtu - 10, startSequence)
    private val protector = SrtpOutboundProtector(ssrc, keys)

    /** Emits encrypted packets in transmission order. Caller owns network send/backpressure. */
    @Synchronized fun encode(sample: H264AccessUnit): List<ByteArray> =
        packetizer.packetize(sample.nals, sample.timestampUs).map(protector::protect)
}
