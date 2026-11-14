package dev.kait.media

/** RFC 3550 minimal sender report (zero reception report blocks). */
object RtcpSenderReport {
    private const val NTP_UNIX_OFFSET_SECONDS = 2_208_988_800L

    fun build(ssrc: Int, unixTimeMillis: Long, rtpTimestamp: Long,
              packetCount: Long, octetCount: Long): ByteArray {
        require(unixTimeMillis >= 0)
        require(rtpTimestamp in 0..0xffffffffL)
        require(packetCount in 0..0xffffffffL && octetCount in 0..0xffffffffL)
        val seconds = unixTimeMillis / 1000 + NTP_UNIX_OFFSET_SECONDS
        val fraction = (unixTimeMillis % 1000) * 0x1_0000_0000L / 1000
        val report = ByteArray(28)
        report[0] = 0x80.toByte() // V=2, P=0, RC=0
        report[1] = 200.toByte() // SR
        report[2] = 0
        report[3] = 6 // 7 32-bit words minus one
        put32(report, 4, ssrc.toLong() and 0xffffffffL)
        put32(report, 8, seconds)
        put32(report, 12, fraction)
        put32(report, 16, rtpTimestamp)
        put32(report, 20, packetCount)
        put32(report, 24, octetCount)
        return report
    }

    private fun put32(bytes: ByteArray, offset: Int, value: Long) {
        for (i in 0..3) bytes[offset + i] = (value ushr (24 - i * 8)).toByte()
    }
}
