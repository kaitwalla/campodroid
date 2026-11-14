package dev.kait.media

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RtcpSenderReportTest {
    @Test fun buildsRfc3550SenderReportWithNtpEpoch() {
        val report = RtcpSenderReport.build(0x01020304, 0, 90_000, 12, 3456)
        assertEquals(28, report.size)
        assertContentEquals(byteArrayOf(0x80.toByte(), 200.toByte(), 0, 6), report.copyOfRange(0, 4))
        assertContentEquals(byteArrayOf(1, 2, 3, 4), report.copyOfRange(4, 8))
        assertContentEquals(byteArrayOf(0x83.toByte(), 0xaa.toByte(), 0x7e, 0x80.toByte()),
            report.copyOfRange(8, 12)) // NTP seconds for Unix epoch
        assertContentEquals(byteArrayOf(0, 0, 0, 0), report.copyOfRange(12, 16))
        assertContentEquals(byteArrayOf(0, 1, 0x5f, 0x90.toByte()), report.copyOfRange(16, 20))
        val secured = SrtcpOutboundProtector(0x01020304,
            SrtpKeyDerivation.deriveRtcp(ByteArray(16), ByteArray(14))).protect(report)
        assertEquals(42, secured.size)
        assertContentEquals(report.copyOfRange(0, 8), secured.copyOfRange(0, 8))
    }

    @Test fun rejectsCounterOverflow() {
        assertFailsWith<IllegalArgumentException> {
            RtcpSenderReport.build(1, 0, 0, 0x1_0000_0000L, 0)
        }
    }
}
