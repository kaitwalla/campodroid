package dev.kait.hap

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/** Self-contained HAP crypto. Uses Bouncy Castle's lightweight primitives on Android and JVM. */
object HapCrypto {
    fun hkdf(key: ByteArray, salt: String, info: String, length: Int = 32): ByteArray {
        require(length in 1..255 * 64)
        val generator = HKDFBytesGenerator(SHA512Digest())
        generator.init(HKDFParameters(key, salt.toByteArray(), info.toByteArray()))
        return ByteArray(length).also { generator.generateBytes(it, 0, length) }
    }

    /** Result is ciphertext followed by a 16-byte authentication tag. */
    fun seal(key: ByteArray, nonceLabel: String, plain: ByteArray): ByteArray = crypt(true, key, nonceLabel, plain)

    @Throws(InvalidCipherTextException::class)
    fun open(key: ByteArray, nonceLabel: String, encrypted: ByteArray): ByteArray = crypt(false, key, nonceLabel, encrypted)

    private fun crypt(encrypt: Boolean, key: ByteArray, nonceLabel: String, input: ByteArray): ByteArray {
        require(key.size == 32 && nonceLabel.toByteArray().size == 8)
        if (!encrypt && input.size < 16) throw InvalidCipherTextException("Short authentication tag")
        val nonce = ByteArray(4) + nonceLabel.toByteArray(Charsets.US_ASCII)
        val cipher = ChaCha20Poly1305()
        cipher.init(encrypt, AEADParameters(KeyParameter(key), 128, nonce, null))
        val output = ByteArray(cipher.getOutputSize(input.size))
        val length = cipher.processBytes(input, 0, input.size, output, 0)
        val tail = cipher.doFinal(output, length)
        return output.copyOf(length + tail)
    }

    fun verifyEd25519(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        if (publicKey.size != 32 || signature.size != 64) return false
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(data, 0, data.size)
        return verifier.verifySignature(signature)
    }

    fun signEd25519(seed: ByteArray, data: ByteArray): ByteArray {
        require(seed.size == 32)
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(seed, 0))
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    fun ed25519PublicKey(seed: ByteArray): ByteArray =
        Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded

    fun newSigningSeed(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(32).also(random::nextBytes)
}
