package dev.kait.media

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 3711 AES_CM_128_HMAC_SHA1_80 outbound RTP only.
 * Scoped to one ordered SSRC stream and one fresh negotiated SRTP master key.
 * Rejects CSRC/extensions, gaps, duplicates and unordered RTP rather than risking nonce reuse.
 * SRTCP, inbound replay, rekeying and HomeKit session negotiation are separate components.
 */
class SrtpOutboundProtector(ssrc: Int, keys: SrtpKeyDerivation.SessionKeys) {
    private val streamSsrc = ssrc
    private val encryption = keys.encryption.copyOf()
    private val authentication = keys.authentication.copyOf()
    private val salt = keys.salt.copyOf()
    private var lastSequence: Int? = null
    private var rolloverCounter = 0L

    init { require(encryption.size == 16 && authentication.size == 20 && salt.size == 14) }

    @Synchronized fun protect(rtp: ByteArray): ByteArray {
        require(rtp.size > 12 && rtp.size <= 65507 - 10)
        require((rtp[0].toInt() and 0xff) == 0x80) { "Only RTP v2 without extensions/CSRC is supported" }
        val observedSsrc = (8..11).fold(0) { accumulator, i -> (accumulator shl 8) or (rtp[i].toInt() and 255) }
        require(observedSsrc == streamSsrc) { "SRTP SSRC mismatch" }
        val sequence = ((rtp[2].toInt() and 255) shl 8) or (rtp[3].toInt() and 255)
        lastSequence?.let { last ->
            require(sequence == ((last + 1) and 0xffff)) { "Out-of-order or repeated RTP packet" }
        }
        val nextRoc = if (lastSequence == 65535 && sequence == 0) rolloverCounter + 1 else rolloverCounter
        require(nextRoc <= 0xffffffffL)
        val packetIndex = (nextRoc shl 16) or sequence.toLong()

        val iv = ByteArray(16)
        salt.copyInto(iv)
        for (index in 0..3) iv[4 + index] = (iv[4 + index].toInt() xor
            ((streamSsrc ushr ((3 - index) * 8)) and 255)).toByte()
        for (index in 0..5) iv[8 + index] = (iv[8 + index].toInt() xor
            ((packetIndex ushr ((5 - index) * 8)) and 255).toInt()).toByte()

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryption, "AES"), IvParameterSpec(iv))
        val encrypted = cipher.doFinal(rtp, 12, rtp.size - 12)
        val sealed = rtp.copyOfRange(0, 12) + encrypted
        val rocBytes = ByteArray(4) { i -> (nextRoc ushr ((3 - i) * 8)).toByte() }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(authentication, "HmacSHA1"))
        mac.update(sealed)
        mac.update(rocBytes)
        val tag = mac.doFinal().copyOf(10)
        // Advance the state only after encryption and authentication succeed.
        lastSequence = sequence
        rolloverCounter = nextRoc
        return sealed + tag
    }
}
