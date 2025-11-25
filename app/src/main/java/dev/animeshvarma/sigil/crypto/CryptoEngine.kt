package dev.animeshvarma.sigil.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
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

/**
 * CryptoEngine provides cryptographic operations using Bouncy Castle library.
 * Supports key derivation with Argon2id and encryption/decryption with AES-256 (GCM),
 * Twofish (CBC), and Serpent (CBC).
 */
object CryptoEngine {

    private val secureRandom = SecureRandom()

    /**
     * Derives a key from a password using Argon2id algorithm.
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        outputLength: Int = 32
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(4)
            .withMemoryPowOfTwo(16)  // 64 MB
            .withParallelism(4)
            .withSalt(salt)
            .build()

        val generator = Argon2BytesGenerator()
        generator.init(params)

        val result = ByteArray(outputLength)
        generator.generateBytes(password, result, 0, outputLength)

        return result
    }

    /**
     * Encrypts data using the specified algorithm.
     */
    fun encrypt(
        data: String,
        password: String,
        algorithm: Algorithm = Algorithm.AES_GCM,
        logCallback: (String) -> Unit = {}
    ): String {
        val startTime = currentTimeMillis()
        val passwordArray = password.toCharArray()

        // Generate salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)
        logCallback("Generated 16-byte salt")

        // Derive key
        val deriveKeyStartTime = currentTimeMillis()
        val key = deriveKey(passwordArray, salt)
        val deriveKeyTime = currentTimeMillis() - deriveKeyStartTime
        logCallback("Argon2id key generated in ${deriveKeyTime}ms")

        // Generate IV
        val iv = when (algorithm) {
            Algorithm.AES_GCM -> ByteArray(12).apply { secureRandom.nextBytes(this) }
            Algorithm.TWOFISH_CBC, Algorithm.SERPENT_CBC -> ByteArray(16).apply { secureRandom.nextBytes(this) }
        }
        logCallback("Generated ${iv.size}-byte IV for ${algorithm.name}")

        // Encrypt
        val cipherText = when (algorithm) {
            Algorithm.AES_GCM -> encryptAES_GCM(data.toByteArray(), key, iv)
            Algorithm.TWOFISH_CBC -> encryptTwofish_CBC(data.toByteArray(), key, iv)
            Algorithm.SERPENT_CBC -> encryptSerpent_CBC(data.toByteArray(), key, iv)
        }

        clearCharArray(passwordArray)

        val totalEncryptionTime = currentTimeMillis() - startTime
        logCallback("${algorithm.name} encryption completed in ${totalEncryptionTime}ms")

        // Format output
        val encoder = Base64.getEncoder().withoutPadding()
        val saltB64 = encoder.encodeToString(salt)
        val ivB64 = encoder.encodeToString(iv)
        val cipherB64 = encoder.encodeToString(cipherText)

        return "$saltB64:$ivB64:$cipherB64"
    }

    /**
     * Decrypts data using the specified algorithm.
     */
    fun decrypt(
        encryptedData: String,
        password: String,
        algorithm: Algorithm = Algorithm.AES_GCM,
        logCallback: (String) -> Unit = {}
    ): String {
        val parts = encryptedData.split(":")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid encrypted data format.")
        }

        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[0])
        val iv = decoder.decode(parts[1])
        val cipherText = decoder.decode(parts[2])

        val startTime = currentTimeMillis()
        val passwordArray = password.toCharArray()

        logCallback("Parsed encrypted data format")

        // Derive key
        val deriveKeyStartTime = currentTimeMillis()
        val key = deriveKey(passwordArray, salt)
        val deriveKeyTime = currentTimeMillis() - deriveKeyStartTime
        logCallback("Argon2id key generated in ${deriveKeyTime}ms")

        // Decrypt
        val decryptedData = when (algorithm) {
            Algorithm.AES_GCM -> decryptAES_GCM(cipherText, key, iv)
            Algorithm.TWOFISH_CBC -> decryptTwofish_CBC(cipherText, key, iv)
            Algorithm.SERPENT_CBC -> decryptSerpent_CBC(cipherText, key, iv)
        }

        clearCharArray(passwordArray)

        val totalDecryptionTime = currentTimeMillis() - startTime
        logCallback("${algorithm.name} decryption completed in ${totalDecryptionTime}ms")

        return String(decryptedData)
    }

    // --- IMPLEMENTATIONS (Using .newInstance() to fix Deprecation Warnings) ---

    private fun encryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // FIXED: Use factory method
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        val parameters = ParametersWithIV(KeyParameter(key), iv)

        cipher.init(true, parameters)

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    private fun decryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // FIXED: Use factory method
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        val parameters = ParametersWithIV(KeyParameter(key), iv)

        cipher.init(false, parameters)

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    private fun encryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // FIXED: Use factory method
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        val parameters = ParametersWithIV(KeyParameter(key), iv)

        cipher.init(true, parameters)

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    private fun decryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // FIXED: Use factory method
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        val parameters = ParametersWithIV(KeyParameter(key), iv)

        cipher.init(false, parameters)

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    private fun encryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // FIXED: Use factory method
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        val parameters = ParametersWithIV(KeyParameter(key), iv)

        cipher.init(true, parameters)

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    private fun decryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        // FIXED: Use factory method
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        val parameters = ParametersWithIV(KeyParameter(key), iv)

        cipher.init(false, parameters)

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    private fun clearCharArray(array: CharArray) {
        for (i in array.indices) {
            array[i] = '\u0000'
        }
    }

    enum class Algorithm {
        AES_GCM,
        TWOFISH_CBC,
        SERPENT_CBC
    }
}