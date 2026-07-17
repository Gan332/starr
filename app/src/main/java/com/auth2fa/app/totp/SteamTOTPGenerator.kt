package com.auth2fa.app.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Steam TOTP implementation.
 * Steam uses a custom TOTP: 30s period, but instead of returning N digits,
 * it returns a 5-character alphanumeric code generated from the hash.
 */
object SteamTOTPGenerator {

    private const val HMAC_ALGORITHM = "HmacSHA1"
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val STEAM_CHARS = "23456789BCDFGHJKMNPQRTVWXY".toCharArray()

    /**
     * Decode a Base32-encoded string to a byte array.
     */
    private fun base32Decode(secret: String): ByteArray {
        val cleaned = secret.replace("[^A-Za-z2-7]".toRegex(), "").uppercase()
        val output = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0

        for (c in cleaned) {
            val value = BASE32_CHARS.indexOf(c)
            if (value == -1) throw IllegalArgumentException("Invalid Base32 character: $c")
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                output.add(((buffer ushr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return output.toByteArray()
    }

    /**
     * Generate a Steam TOTP code.
     * @param secret Base32-encoded secret key
     * @param currentTimeSeconds Current unix time in seconds
     * @return 5-character Steam alphanumeric code
     */
    fun generate(
        secret: String,
        currentTimeSeconds: Long = System.currentTimeMillis() / 1000
    ): String {
        // Steam uses a 30-second period like standard TOTP
        val period = 30L
        val counter = currentTimeSeconds / period

        // Build 8-byte big-endian counter
        val counterBytes = ByteArray(8)
        var temp = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (temp and 0xFF).toByte()
            temp = temp ushr 8
        }

        // HMAC-SHA1
        val keyBytes = base32Decode(secret)
        val keySpec = SecretKeySpec(keyBytes, HMAC_ALGORITHM)
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(keySpec)
        val hash = mac.doFinal(counterBytes)

        // Steam-style code generation
        // Start at the offset byte (last nibble of hash)
        var offset = hash[hash.size - 1].toInt() and 0xF
        var fullCode = 0
        for (i in 0 until 4) {
            fullCode = (fullCode shl 8) or (hash[offset + i].toInt() and 0xFF)
        }
        fullCode = fullCode and 0x7FFFFFFF

        // Generate 5-character Steam code
        val code = StringBuilder()
        var remaining = fullCode
        for (i in 0 until 5) {
            code.append(STEAM_CHARS[remaining % STEAM_CHARS.size])
            remaining /= STEAM_CHARS.size
        }
        return code.toString()
    }

    /**
     * Get remaining seconds in the current time period.
     */
    fun getTimeRemaining(currentTimeSeconds: Long = System.currentTimeMillis() / 1000): Int {
        val period = 30
        return period - (currentTimeSeconds % period).toInt()
    }

    /**
     * Get progress (0.0 - 1.0) through the current time period.
     */
    fun getProgress(currentTimeSeconds: Long = System.currentTimeMillis() / 1000): Float {
        val period = 30
        return (currentTimeSeconds % period).toFloat() / period
    }
}
