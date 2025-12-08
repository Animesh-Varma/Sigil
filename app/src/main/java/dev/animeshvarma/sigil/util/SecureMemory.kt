package dev.animeshvarma.sigil.util

import java.util.Arrays
import kotlin.math.log2
import kotlin.math.pow

object SecureMemory {

    /**
     * Wipes a ByteArray by overwriting it with zeros.
     */
    fun wipe(data: ByteArray) {
        Arrays.fill(data, 0.toByte())
    }

    /**
     * Wipes a CharArray by overwriting it with null characters.
     */
    fun wipe(data: CharArray) {
        Arrays.fill(data, '\u0000')
    }

    data class EntropyResult(
        val score: Int, // 0 to 100
        val label: String,
        val colorHex: Long // ARGB Long
    )

    /**
     * heuristic to estimate password strength.
     */
    fun calculateEntropy(password: String): EntropyResult {
        if (password.isEmpty()) return EntropyResult(0, "Empty", 0xFFB00020)

        var poolSize = 0
        if (password.any { it.isLowerCase() }) poolSize += 26
        if (password.any { it.isUpperCase() }) poolSize += 26
        if (password.any { it.isDigit() }) poolSize += 10
        if (password.any { !it.isLetterOrDigit() }) poolSize += 32

        val entropy = password.length * log2(poolSize.toDouble())

        val score = (entropy.coerceAtMost(100.0)).toInt()

        return when {
            score < 40 -> EntropyResult(score, "Weak", 0xFFCF6679) // Red
            score < 75 -> EntropyResult(score, "Moderate", 0xFFFFD54F) // Yellow
            score < 95 -> EntropyResult(score, "Strong", 0xFF81C784) // Green
            else -> EntropyResult(score, "Unbreakable", 0xFF00E676) // Bright Green
        }
    }
}