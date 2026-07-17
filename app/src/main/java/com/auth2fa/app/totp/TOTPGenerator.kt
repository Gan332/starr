package com.auth2fa.app.totp

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP (RFC 6238) implementation using HMAC-SHA1.
 */
object TOTPGenerator {

    private const val HMAC_ALGORITHM = "HmacSHA1"
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * Decode a Base32-encoded string to a byte array.
     */
    fun base32Decode(secret: String): ByteArray {
        val cleaned = secret.replace("[-\\s]".toRegex(), "").uppercase()
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
     * Generate a TOTP code for the given secret at the current time.
     * @param secret Base32-encoded secret key
     * @param digits Number of digits (default 6)
     * @param period Time period in seconds (default 30)
     * @return The TOTP code as a string
     */
    fun generate(
        secret: String,
        digits: Int = 6,
        period: Int = 30
    ): String {
        val counter = System.currentTimeMillis() / 1000 / period
        return generateForCounter(secret, counter, digits)
    }

    /**
     * Generate a TOTP code for a given counter value.
     */
    private fun generateForCounter(secret: String, counter: Long, digits: Int): String {
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

        // Dynamic truncation (RFC 4226)
        val offset = hash[hash.size - 1].toInt() and 0xF
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                     ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                     ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                     (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % Math.pow(10.0, digits.toDouble()).toInt()
        return otp.toString().padStart(digits, '0')
    }

    /**
     * Get remaining seconds in the current time period.
     */
    fun getTimeRemaining(period: Int = 30): Int {
        return period - ((System.currentTimeMillis() / 1000) % period).toInt()
    }

    /**
     * Get progress (0.0 - 1.0) through the current time period.
     */
    fun getProgress(period: Int = 30): Float {
        return ((System.currentTimeMillis() / 1000) % period).toFloat() / period
    }
}
