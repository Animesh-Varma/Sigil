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

object CryptoEngine {

    private val secureRandom = SecureRandom()

    // --- ENCRYPTION CHAIN ---
    fun encrypt(
        data: String,
        password: String,
        // Default chain is just AES, but now supports [AES, TWOFISH], etc.
        algorithms: List<Algorithm> = listOf(Algorithm.AES_GCM),
        logCallback: (String) -> Unit = {}
    ): String {
        val startTime = currentTimeMillis()

        // 1. Setup
        val passwordArray = password.toCharArray()
        val encoder = Base64.getEncoder()

        // 2. Generate Master Salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        // 3. Derive Master Key (Argon2id)
        val key = deriveKey(passwordArray, salt)
        logCallback("Argon2id Master Key derived")

        // 4. Chain Processing
        var currentData = data.toByteArray()
        val ivList = mutableListOf<String>()
        val algoIdList = algorithms.joinToString(",") { it.name }

        logCallback("Starting Encryption Chain: $algoIdList")

        algorithms.forEachIndexed { index, algo ->
            // Generate IV for this specific layer
            val ivSize = if (algo == Algorithm.AES_GCM) 12 else 16
            val iv = ByteArray(ivSize).apply { secureRandom.nextBytes(this) }
            ivList.add(encoder.encodeToString(iv))

            // Encrypt this layer
            currentData = when (algo) {
                Algorithm.AES_GCM -> encryptAES_GCM(currentData, key, iv)
                Algorithm.TWOFISH_CBC -> encryptTwofish_CBC(currentData, key, iv)
                Algorithm.SERPENT_CBC -> encryptSerpent_CBC(currentData, key, iv)
            }
            logCallback("Layer ${index + 1} (${algo.name}) finished")
        }

        clearCharArray(passwordArray)

        // 5. Pack the Output
        // Format: SALT : ALGO_LIST : IV_LIST : CIPHERTEXT
        val saltB64 = encoder.encodeToString(salt)
        val ivsString = ivList.joinToString(",")
        val cipherB64 = encoder.encodeToString(currentData)

        logCallback("Encryption finished in ${currentTimeMillis() - startTime}ms")

        return "$saltB64:$algoIdList:$ivsString:$cipherB64"
    }

    // --- DECRYPTION CHAIN ---
    fun decrypt(
        encryptedData: String,
        password: String,
        logCallback: (String) -> Unit = {}
    ): String {
        // 1. Parse the "Onion" Header
        val parts = encryptedData.split(":")
        if (parts.size != 4) {
            throw IllegalArgumentException("Invalid Sigil format. Expected 4 parts, got ${parts.size}")
        }

        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[0])
        val algoNames = parts[1].split(",")
        val ivStrings = parts[2].split(",")
        val rawCipher = decoder.decode(parts[3])

        if (algoNames.size != ivStrings.size) {
            throw IllegalArgumentException("Tampered Data: Algo/IV count mismatch")
        }

        // 2. Derive Master Key
        val passwordArray = password.toCharArray()
        val key = deriveKey(passwordArray, salt)
        logCallback("Master Key derived. Peeling ${algoNames.size} layers...")

        // 3. Reverse Chain Processing (Peeling the Onion)
        var currentData = rawCipher

        // We must loop BACKWARDS (Last layer applied is first to be removed)
        for (i in algoNames.indices.reversed()) {
            val algoName = algoNames[i]
            val iv = decoder.decode(ivStrings[i])
            val algorithm = Algorithm.valueOf(algoName)

            currentData = when (algorithm) {
                Algorithm.AES_GCM -> decryptAES_GCM(currentData, key, iv)
                Algorithm.TWOFISH_CBC -> decryptTwofish_CBC(currentData, key, iv)
                Algorithm.SERPENT_CBC -> decryptSerpent_CBC(currentData, key, iv)
            }
            logCallback("Layer ${i + 1} ($algoName) decrypted")
        }

        clearCharArray(passwordArray)
        return String(currentData)
    }

    // --- CORE PRIMITIVES (Keep these as they were) ---

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
        cipher.doFinal(output, len)
        return output
    }

    private fun decryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        cipher.doFinal(output, len)
        return output
    }

    private fun encryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        cipher.init(true, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        cipher.doFinal(output, len)
        return output
    }

    private fun decryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        cipher.doFinal(output, len)
        return output
    }

    private fun encryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        cipher.init(true, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        cipher.doFinal(output, len)
        return output
    }

    private fun decryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        cipher.doFinal(output, len)
        return output
    }

    private fun clearCharArray(array: CharArray) {
        for (i in array.indices) array[i] = '\u0000'
    }

    enum class Algorithm { AES_GCM, TWOFISH_CBC, SERPENT_CBC }
}