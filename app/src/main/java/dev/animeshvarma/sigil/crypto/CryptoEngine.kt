package dev.animeshvarma.sigil.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.engines.TwofishEngine
import org.bouncycastle.crypto.engines.SerpentEngine
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import java.security.SecureRandom
import java.util.Base64
import java.lang.System.currentTimeMillis
import java.nio.charset.StandardCharsets

object CryptoEngine {

    private val secureRandom = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    // Active Integrity Check Trailer
    private const val SIGIL_SENTINEL = "::SIGIL_EOF::"

    fun encrypt(
        data: String,
        password: String,
        algorithms: List<Algorithm> = listOf(Algorithm.AES_GCM),
        logCallback: (String) -> Unit = {}
    ): String {
        val startTime = currentTimeMillis()
        logCallback("Initializing Secure Chain...")

        val passwordArray = password.toCharArray()
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        val key = deriveKey(passwordArray, salt)
        logCallback("Master Key derived (Argon2id).")

        // 1. Attach Sentinel (Active Integrity)
        val taggedData = data + SIGIL_SENTINEL
        var currentBytes = taggedData.toByteArray(StandardCharsets.UTF_8)

        val ivList = mutableListOf<String>()
        val algoNames = algorithms.joinToString(",") { it.name }

        logCallback("Chain Sequence: $algoNames")

        algorithms.forEachIndexed { index, algo ->
            val ivSize = if (algo == Algorithm.AES_GCM) 12 else 16
            val iv = ByteArray(ivSize).apply { secureRandom.nextBytes(this) }
            ivList.add(encoder.encodeToString(iv))

            logCallback("Layer ${index + 1}: Encrypting (${algo.name})...")

            // Critical Fix: Now returns exact byte size, no trailing zeros
            currentBytes = when (algo) {
                Algorithm.AES_GCM -> encryptAES_GCM(currentBytes, key, iv)
                Algorithm.TWOFISH_CBC -> encryptTwofish_CBC(currentBytes, key, iv)
                Algorithm.SERPENT_CBC -> encryptSerpent_CBC(currentBytes, key, iv)
            }
        }

        val bodyCiphertext = encoder.encodeToString(currentBytes)

        val metadataString = "$algoNames|${ivList.joinToString(",")}"
        val metadataBytes = metadataString.toByteArray(StandardCharsets.UTF_8)

        val headerIv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val encryptedMetadataBytes = encryptAES_GCM(metadataBytes, key, headerIv)
        val encryptedMetadataB64 = encoder.encodeToString(encryptedMetadataBytes)
        val headerIvB64 = encoder.encodeToString(headerIv)
        val saltB64 = encoder.encodeToString(salt)

        logCallback("Header encrypted & sealed.")
        clearCharArray(passwordArray)
        logCallback("Operation complete in ${currentTimeMillis() - startTime}ms.")

        return "$saltB64:$headerIvB64:$encryptedMetadataB64:$bodyCiphertext"
    }

    fun decrypt(
        encryptedData: String,
        password: String,
        logCallback: (String) -> Unit = {}
    ): String {
        logCallback("Reading Secure Container...")

        // Sanitize: Remove generic clipboard garbage
        val cleanData = encryptedData.filter { !it.isWhitespace() }
        val parts = cleanData.split(":")

        if (parts.size != 4) throw IllegalArgumentException("Invalid format. Expected 4 parts.")

        try {
            val salt = decoder.decode(parts[0])
            val headerIv = decoder.decode(parts[1])
            val encryptedMetadata = decoder.decode(parts[2])
            val bodyCiphertext = decoder.decode(parts[3])

            val passwordArray = password.toCharArray()
            val key = deriveKey(passwordArray, salt)
            logCallback("Master Key derived.")

            logCallback("Decrypting Header Metadata...")
            val metadataBytes = try {
                decryptAES_GCM(encryptedMetadata, key, headerIv)
            } catch (e: Exception) {
                throw IllegalArgumentException("Wrong Password or Header Tampered.")
            }

            val metadataString = String(metadataBytes, StandardCharsets.UTF_8)
            val metaParts = metadataString.split("|")
            val algoNames = metaParts[0].split(",")
            val ivStrings = metaParts[1].split(",")

            logCallback("Layers detected: ${metaParts[0]}")

            var currentBytes = bodyCiphertext

            for (i in algoNames.indices.reversed()) {
                val algoName = algoNames[i]
                val iv = decoder.decode(ivStrings[i])
                val algorithm = Algorithm.valueOf(algoName)

                logCallback("Layer ${i + 1}: Decrypting ($algoName)...")

                currentBytes = when (algorithm) {
                    Algorithm.AES_GCM -> decryptAES_GCM(currentBytes, key, iv)
                    Algorithm.TWOFISH_CBC -> decryptTwofish_CBC(currentBytes, key, iv)
                    Algorithm.SERPENT_CBC -> decryptSerpent_CBC(currentBytes, key, iv)
                }
            }

            clearCharArray(passwordArray)

            // 2. Active Fixing (Sentinel Check)
            val rawResult = String(currentBytes, StandardCharsets.UTF_8)
            if (rawResult.contains(SIGIL_SENTINEL)) {
                logCallback("Integrity Check Passed (Sentinel Found).")
                // Return everything BEFORE the sentinel, discarding any padding garbage
                return rawResult.substringBeforeLast(SIGIL_SENTINEL)
            } else {
                logCallback("WARNING: Sentinel missing. Data might be truncated.")
                return rawResult // Return best effort
            }

        } catch (e: Exception) {
            throw IllegalArgumentException("Decryption Error: ${e.message}")
        }
    }

    // --- PRIMITIVES (FIXED: Added .copyOf(len + finalLen)) ---

    fun deriveKey(password: CharArray, salt: ByteArray, outputLength: Int = 32): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(4)
            .withMemoryPowOfTwo(16)
            .withParallelism(4)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val result = ByteArray(outputLength)
        generator.generateBytes(password, result, 0, outputLength)
        return result
    }

    private fun encryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(true, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        // FIX: Trim buffer to actual size
        return output.copyOf(len + finalLen)
    }

    private fun decryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        // FIX: Trim buffer to actual size
        return output.copyOf(len + finalLen)
    }

    private fun encryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        cipher.init(true, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        // FIX: Trim buffer to actual size
        return output.copyOf(len + finalLen)
    }

    private fun decryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        // FIX: Trim buffer to actual size
        return output.copyOf(len + finalLen)
    }

    private fun encryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        cipher.init(true, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        // FIX: Trim buffer to actual size
        return output.copyOf(len + finalLen)
    }

    private fun decryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        // FIX: Trim buffer to actual size
        return output.copyOf(len + finalLen)
    }

    private fun clearCharArray(array: CharArray) {
        for (i in array.indices) array[i] = '\u0000'
    }

    enum class Algorithm { AES_GCM, TWOFISH_CBC, SERPENT_CBC }
}