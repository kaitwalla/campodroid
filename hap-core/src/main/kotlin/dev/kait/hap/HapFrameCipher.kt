package dev.kait.hap

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.ByteArrayOutputStream

/** HAP encrypted HTTP frame transport: 2-byte LE length, AEAD ciphertext, 16-byte tag. */
class HapFrameCipher(private val readKey: ByteArray, private val writeKey: ByteArray) {
    init { require(readKey.size == 32 && writeKey.size == 32) }
    private var readCounter = 0L
    private var writeCounter = 0L
    private var pending = ByteArray(0)
    private var invalid = false

    /** Each HAP frame has no more than 1024 bytes of plaintext. */
    @Synchronized fun encode(plaintext: ByteArray): ByteArray {
        check(!invalid)
        val out = ByteArrayOutputStream()
        var offset = 0
        while (offset < plaintext.size) {
            val size = minOf(1024, plaintext.size - offset)
            val length = byteArrayOf(size.toByte(), (size ushr 8).toByte())
            val encrypted = crypt(true, writeKey, writeCounter++, length, plaintext.copyOfRange(offset, offset + size))
            out.write(length)
            out.write(encrypted)
            offset += size
        }
        return out.toByteArray()
    }

    /** Feed any TCP-sized chunk, returning zero or more fully authenticated plaintext fragments. */
    @Synchronized fun decode(chunk: ByteArray): List<ByteArray> {
        check(!invalid)
        require(chunk.size <= 64 * 1024) { "Excessive network input chunk" }
        require(pending.size + chunk.size <= 128 * 1024) { "Excessive pending frame buffer" }
        pending += chunk
        val result = mutableListOf<ByteArray>()
        var offset = 0
        try {
            while (pending.size - offset >= 2) {
                val size = (pending[offset].toInt() and 255) or ((pending[offset + 1].toInt() and 255) shl 8)
                require(size in 0..1024) { "Invalid HAP frame size" }
                if (pending.size - offset < size + 18) break
                val aad = pending.copyOfRange(offset, offset + 2)
                val sealed = pending.copyOfRange(offset + 2, offset + size + 18)
                result.add(crypt(false, readKey, readCounter, aad, sealed))
                readCounter++
                offset += size + 18
            }
            pending = pending.copyOfRange(offset, pending.size)
            return result
        } catch (e: Exception) {
            invalid = true // Transport must close this session on authentication failure.
            pending = ByteArray(0)
            throw e
        }
    }

    private fun crypt(encrypt: Boolean, key: ByteArray, counter: Long, aad: ByteArray, input: ByteArray): ByteArray {
        val nonce = ByteArray(12)
        for (i in 0 until 8) nonce[4 + i] = (counter ushr (i * 8)).toByte()
        val cipher = ChaCha20Poly1305()
        cipher.init(encrypt, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val output = ByteArray(cipher.getOutputSize(input.size))
        val count = cipher.processBytes(input, 0, input.size, output, 0)
        return output.copyOf(count + cipher.doFinal(output, count))
    }
}
