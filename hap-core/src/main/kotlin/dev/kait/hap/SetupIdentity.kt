package dev.kait.hap

import java.security.SecureRandom
import java.util.Locale

/** HomeKit IP setup metadata. Do not display QR pairing until the HAP server is functional. */
data class SetupIdentity(val code: String, val setupId: String) {
    init {
        require(SetupIdentityFactory.isValidCode(code))
        require(setupId.matches(Regex("[A-Z0-9]{4}")))
    }

    /** HAP's X-HM URI payload; conservative category 2 matches Pylo's bridge discovery. */
    fun setupUri(category: Int = 2): String {
        require(category in 1..15) { "HAP QR accessory category is a four-bit field" }
        val value = code.filter(Char::isDigit).toLong()
        val payload = value or (category.toLong() shl 27) or (2L shl 31)
        return "X-HM://${payload.toString(36).uppercase(Locale.ROOT).padStart(9, '0')}$setupId"
    }
}

object SetupIdentityFactory {
    private val invalid = buildSet {
        add("000-00-000")
        add("123-45-678")
        add("876-54-321")
        for (digit in '1'..'9') add("$digit$digit$digit-$digit$digit-$digit$digit$digit")
    }

    fun isValidCode(code: String): Boolean =
        code.matches(Regex("[0-9]{3}-[0-9]{2}-[0-9]{3}")) && code !in invalid

    fun generate(random: SecureRandom = SecureRandom()): SetupIdentity {
        while (true) {
            val digits = CharArray(8) { ('0'.code + random.nextInt(10)).toChar() }
            val code = "${String(digits, 0, 3)}-${String(digits, 3, 2)}-${String(digits, 5, 3)}"
            if (!isValidCode(code)) continue
            val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val id = CharArray(4) { alphabet[random.nextInt(alphabet.length)] }
            return SetupIdentity(code, String(id))
        }
    }
}
