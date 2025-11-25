package dev.animeshvarma.sigil.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.generators.SCrypt
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
     *
     * @param password The password as a CharArray
     * @param salt The salt for key derivation
     * @param outputLength The desired output length in bytes (default 32)
     * @return The derived key as a ByteArray
     */
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        outputLength: Int = 32
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(4)  // 4 iterations
            .withMemoryPowOfTwo(16)  // 2^16 = 65536 KB = 64 MB
            .withParallelism(4)  // 4 threads
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
     *
     * @param data The plaintext data to encrypt
     * @param password The password for encryption
     * @param algorithm The cipher algorithm to use (default AES-GCM)
     * @param logCallback A callback to report progress/logging
     * @return The encrypted data in format [SALT_B64]:[IV_B64]:[CIPHER_B64]
     */
    fun encrypt(
        data: String,
        password: String,
        algorithm: Algorithm = Algorithm.AES_GCM,
        logCallback: (String) -> Unit = {}
    ): String {
        val startTime = currentTimeMillis()

        // Convert password to CharArray for secure handling
        val passwordArray = password.toCharArray()

        // Generate salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        logCallback("Generated 16-byte salt")

        // Derive key using Argon2id
        val deriveKeyStartTime = currentTimeMillis()
        val key = deriveKey(passwordArray, salt)
        val deriveKeyTime = currentTimeMillis() - deriveKeyStartTime
        logCallback("Argon2id key generated in ${deriveKeyTime}ms")

        // Generate IV based on algorithm
        val iv = when (algorithm) {
            Algorithm.AES_GCM -> {
                val ivBytes = ByteArray(12)  // 12 bytes for GCM
                secureRandom.nextBytes(ivBytes)
                ivBytes
            }
            Algorithm.TWOFISH_CBC, Algorithm.SERPENT_CBC -> {
                val ivBytes = ByteArray(16)  // 16 bytes for CBC
                secureRandom.nextBytes(ivBytes)
                ivBytes
            }
        }

        logCallback("Generated ${iv.size}-byte IV for ${algorithm.name}")

        // Encrypt based on algorithm
        val cipherText = when (algorithm) {
            Algorithm.AES_GCM -> {
                encryptAES_GCM(data.toByteArray(), key, iv, logCallback)
            }
            Algorithm.TWOFISH_CBC -> {
                encryptTwofish_CBC(data.toByteArray(), key, iv, logCallback)
            }
            Algorithm.SERPENT_CBC -> {
                encryptSerpent_CBC(data.toByteArray(), key, iv, logCallback)
            }
        }

        // Clear password from memory
        clearCharArray(passwordArray)

        val totalEncryptionTime = currentTimeMillis() - startTime
        logCallback("${algorithm.name} encryption completed in ${totalEncryptionTime}ms")

        // Format output as [SALT_B64]:[IV_B64]:[CIPHER_B64]
        val saltB64 = Base64.getEncoder().withoutPadding().encodeToString(salt)
        val ivB64 = Base64.getEncoder().withoutPadding().encodeToString(iv)
        val cipherB64 = Base64.getEncoder().withoutPadding().encodeToString(cipherText)

        return "$saltB64:$ivB64:$cipherB64"
    }

    /**
     * Decrypts data using the specified algorithm.
     *
     * @param encryptedData The encrypted data in format [SALT_B64]:[IV_B64]:[CIPHER_B64]
     * @param password The password for decryption
     * @param algorithm The cipher algorithm to use (default AES-GCM)
     * @param logCallback A callback to report progress/logging
     * @return The decrypted plaintext
     */
    fun decrypt(
        encryptedData: String,
        password: String,
        algorithm: Algorithm = Algorithm.AES_GCM,
        logCallback: (String) -> Unit = {}
    ): String {
        // Parse the encrypted data
        val parts = encryptedData.split(":")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid encrypted data format. Expected [SALT_B64]:[IV_B64]:[CIPHER_B64]")
        }

        val salt = Base64.getDecoder().decode(parts[0])
        val iv = Base64.getDecoder().decode(parts[1])
        val cipherText = Base64.getDecoder().decode(parts[2])

        val startTime = currentTimeMillis()

        // Convert password to CharArray for secure handling
        val passwordArray = password.toCharArray()

        logCallback("Parsed encrypted data format")

        // Derive key using Argon2id
        val deriveKeyStartTime = currentTimeMillis()
        val key = deriveKey(passwordArray, salt)
        val deriveKeyTime = currentTimeMillis() - deriveKeyStartTime
        logCallback("Argon2id key generated in ${deriveKeyTime}ms")

        // Decrypt based on algorithm
        val decryptedData = when (algorithm) {
            Algorithm.AES_GCM -> {
                decryptAES_GCM(cipherText, key, iv, logCallback)
            }
            Algorithm.TWOFISH_CBC -> {
                decryptTwofish_CBC(cipherText, key, iv, logCallback)
            }
            Algorithm.SERPENT_CBC -> {
                decryptSerpent_CBC(cipherText, key, iv, logCallback)
            }
        }

        // Clear password from memory
        clearCharArray(passwordArray)

        val totalDecryptionTime = currentTimeMillis() - startTime
        logCallback("${algorithm.name} decryption completed in ${totalDecryptionTime}ms")

        return String(decryptedData)
    }

    // AES-GCM implementation
    private fun encryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray, logCallback: (String) -> Unit): ByteArray {
        val cipher = GCMBlockCipher(AESEngine())
        val keyParam = KeyParameter(key)
        val parameters = ParametersWithIV(keyParam, iv)

        cipher.init(true, parameters)  // true for encryption

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    private fun decryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray, logCallback: (String) -> Unit): ByteArray {
        val cipher = GCMBlockCipher(AESEngine())
        val keyParam = KeyParameter(key)
        val parameters = ParametersWithIV(keyParam, iv)

        cipher.init(false, parameters)  // false for decryption

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    // Twofish-CBC implementation
    private fun encryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray, logCallback: (String) -> Unit): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher(TwofishEngine()), PKCS7Padding())
        val keyParam = KeyParameter(key)
        val parameters = ParametersWithIV(keyParam, iv)

        cipher.init(true, parameters)  // true for encryption

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    private fun decryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray, logCallback: (String) -> Unit): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher(TwofishEngine()), PKCS7Padding())
        val keyParam = KeyParameter(key)
        val parameters = ParametersWithIV(keyParam, iv)

        cipher.init(false, parameters)  // false for decryption

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    // Serpent-CBC implementation
    private fun encryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray, logCallback: (String) -> Unit): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher(SerpentEngine()), PKCS7Padding())
        val keyParam = KeyParameter(key)
        val parameters = ParametersWithIV(keyParam, iv)

        cipher.init(true, parameters)  // true for encryption

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    private fun decryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray, logCallback: (String) -> Unit): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher(SerpentEngine()), PKCS7Padding())
        val keyParam = KeyParameter(key)
        val parameters = ParametersWithIV(keyParam, iv)

        cipher.init(false, parameters)  // false for decryption

        val output = ByteArray(cipher.getOutputSize(data.size))
        val processLen = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, processLen)

        return output.sliceArray(0 until processLen + finalLen)
    }

    // Utility to clear char array from memory
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