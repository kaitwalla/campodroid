package dev.kait.media

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** RFC 3711 SRTCP AES_CM_128_HMAC_SHA1_80 protection for RTCP v2 packets. */
class SrtcpOutboundProtector(ssrc: Int, keys: SrtpKeyDerivation.SessionKeys) {
    private val streamSsrc = ssrc
    private val encryption = keys.encryption.copyOf()
    private val authentication = keys.authentication.copyOf()
    private val salt = keys.salt.copyOf()
    private var nextIndex = 0L

    init { require(encryption.size == 16 && authentication.size == 20 && salt.size == 14) }

    @Synchronized fun protect(rtcp: ByteArray): ByteArray {
        require(rtcp.size >= 8 && rtcp.size <= 65507 - 14)
        require((rtcp[0].toInt() and 0xc0) == 0x80) { "RTCP v2 required" }
        val lengthWords = ((rtcp[2].toInt() and 255) shl 8) or (rtcp[3].toInt() and 255)
        require((lengthWords + 1) * 4 == rtcp.size) { "Single RTCP packet required" }
        val observedSsrc = (4..7).fold(0) { value, i -> (value shl 8) or (rtcp[i].toInt() and 255) }
        require(observedSsrc == streamSsrc) { "SRTCP SSRC mismatch" }
        require(nextIndex < 0x80000000L) { "SRTCP index exhausted; renegotiate session" }

        val iv = ByteArray(16)
        salt.copyInto(iv)
        for (i in 0..3) {
            iv[4 + i] = (iv[4 + i].toInt() xor (streamSsrc ushr ((3 - i) * 8) and 255)).toByte()
            iv[10 + i] = (iv[10 + i].toInt() xor (nextIndex ushr ((3 - i) * 8)).toInt()).toByte()
        }
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryption, "AES"), IvParameterSpec(iv))
        val packet = rtcp.copyOfRange(0, 8) + cipher.doFinal(rtcp, 8, rtcp.size - 8)
        val encryptedIndex = (nextIndex or 0x80000000L)
        val indexBytes = ByteArray(4) { i -> (encryptedIndex ushr ((3 - i) * 8)).toByte() }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(authentication, "HmacSHA1"))
        mac.update(packet)
        mac.update(indexBytes)
        val tag = mac.doFinal().copyOf(10)
        nextIndex++
        return packet + indexBytes + tag
    }
}
