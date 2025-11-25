package dev.animeshvarma.sigil.crypto

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
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
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

object CryptoEngine {

    private val secureRandom = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    // Internal Checksum Delimiter (Hidden inside encrypted body)
    private const val CS_DELIMITER = "::SIGIL_CS::"

    fun encrypt(
        data: String,
        password: String,
        algorithms: List<Algorithm> = listOf(Algorithm.AES_GCM),
        logCallback: (String) -> Unit = {}
    ): String {
        val startTime = currentTimeMillis()
        logCallback("Initializing Secure Chain...")

        // 1. Generate Salt (16 Bytes)
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        // 2. Derive ROOT SECRET
        val sha512Password = hashPasswordSHA512(password)
        val rootSecret = deriveRootSecret(sha512Password, salt)
        logCallback("Root Secret derived (SHA512 + Argon2id).")

        // 3. Prepare Payload with Checksum
        val checksum = hashSHA256(data)
        val taggedData = data + CS_DELIMITER + checksum

        var currentBytes = taggedData.toByteArray(StandardCharsets.UTF_8)
        val ivList = mutableListOf<String>()
        val algoNames = algorithms.joinToString(",") { it.name }

        logCallback("Chain Sequence: $algoNames")

        algorithms.forEachIndexed { index, algo ->
            val layerId = index + 1

            // A. Generate Unique IV
            val ivSize = if (algo == Algorithm.AES_GCM) 12 else 16
            val iv = ByteArray(ivSize).apply { secureRandom.nextBytes(this) }
            ivList.add(encoder.encodeToString(iv))

            // B. Derive Unique Key
            val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId")

            logCallback("Layer $layerId: Encrypting (${algo.name})...")

            currentBytes = when (algo) {
                Algorithm.AES_GCM -> encryptAES_GCM(currentBytes, layerKey, iv)
                Algorithm.TWOFISH_CBC -> encryptTwofish_CBC(currentBytes, layerKey, iv)
                Algorithm.SERPENT_CBC -> encryptSerpent_CBC(currentBytes, layerKey, iv)
            }
        }

        // 4. Header Encryption
        val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER")

        val metadataString = "$algoNames|${ivList.joinToString(",")}"
        val metadataBytes = metadataString.toByteArray(StandardCharsets.UTF_8)

        val headerIv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val encryptedMetadataBytes = encryptAES_GCM(metadataBytes, headerKey, headerIv)

        logCallback("Header encrypted & sealed.")

        // 5. Binary Packing (The Stealth Blob)
        val totalSize = 16 + 12 + 4 + encryptedMetadataBytes.size + currentBytes.size
        val byteBuffer = ByteBuffer.allocate(totalSize)

        byteBuffer.put(salt)                 // 16 bytes
        byteBuffer.put(headerIv)             // 12 bytes
        byteBuffer.putInt(encryptedMetadataBytes.size) // 4 bytes
        byteBuffer.put(encryptedMetadataBytes) // Variable
        byteBuffer.put(currentBytes)         // Variable

        val packedBytes = byteBuffer.array()

        // 6. Encode
        val finalOutput = stripPadding(encoder.encodeToString(packedBytes))

        logCallback("Operation complete in ${currentTimeMillis() - startTime}ms.")

        return finalOutput
    }

    fun decrypt(
        encryptedData: String,
        password: String,
        logCallback: (String) -> Unit = {}
    ): String {
        logCallback("Reading Secure Container...")

        // Sanitize Input
        val cleanData = encryptedData.filter { !it.isWhitespace() }

        try {
            // 1. Decode the Monolith
            val packedBytes = decoder.decode(restorePadding(cleanData))
            val buffer = ByteBuffer.wrap(packedBytes)

            // 2. Unpack Binary Structure
            if (buffer.remaining() < 32) throw IllegalArgumentException("Data too short.")
            val salt = ByteArray(16)
            buffer.get(salt)

            val headerIv = ByteArray(12)
            buffer.get(headerIv)

            val headerLength = buffer.int
            if (headerLength < 0 || headerLength > buffer.remaining()) {
                throw IllegalArgumentException("Header length corrupted.")
            }

            val encryptedMetadata = ByteArray(headerLength)
            buffer.get(encryptedMetadata)

            val bodyLength = buffer.remaining()
            val bodyCiphertext = ByteArray(bodyLength)
            buffer.get(bodyCiphertext)

            // 3. Reconstruct Root Secret
            val sha512Password = hashPasswordSHA512(password)
            val rootSecret = deriveRootSecret(sha512Password, salt)
            logCallback("Root Secret reconstructed.")

            // 4. Decrypt Header
            val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER")

            logCallback("Decrypting Header Metadata...")
            val metadataBytes = try {
                decryptAES_GCM(encryptedMetadata, headerKey, headerIv)
            } catch (e: Exception) {
                throw IllegalArgumentException("Wrong password or tampered header.")
            }

            val metadataString = String(metadataBytes, StandardCharsets.UTF_8)
            val metaParts = metadataString.split("|")
            val algoNames = metaParts[0].split(",")
            val ivStrings = metaParts[1].split(",")

            logCallback("Layers detected: ${metaParts[0]}")

            var currentBytes = bodyCiphertext

            // 5. Decrypt Body
            for (i in algoNames.indices.reversed()) {
                val layerId = i + 1
                val algoName = algoNames[i]
                val iv = decoder.decode(ivStrings[i])
                val algorithm = Algorithm.valueOf(algoName)
                val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId")

                logCallback("Layer $layerId: Decrypting (${algoName})...")

                currentBytes = when (algorithm) {
                    Algorithm.AES_GCM -> decryptAES_GCM(currentBytes, layerKey, iv)
                    Algorithm.TWOFISH_CBC -> decryptTwofish_CBC(currentBytes, layerKey, iv)
                    Algorithm.SERPENT_CBC -> decryptSerpent_CBC(currentBytes, layerKey, iv)
                }
            }

            // 6. Verify Integrity (Checksum)
            val rawResult = String(currentBytes, StandardCharsets.UTF_8)

            if (rawResult.contains(CS_DELIMITER)) {
                val plainText = rawResult.substringBeforeLast(CS_DELIMITER)
                val storedChecksum = rawResult.substringAfterLast(CS_DELIMITER)
                val calculatedChecksum = hashSHA256(plainText)

                if (storedChecksum == calculatedChecksum) {
                    logCallback("Integrity Verified (SHA-256 Match).")
                    return plainText
                } else {
                    logCallback("CRITICAL: Checksum Mismatch!")
                    return "ERROR: Decrypted data corrupted."
                }
            } else {
                logCallback("Warning: Legacy format (No checksum).")
                return rawResult
            }

        } catch (e: Exception) {
            throw IllegalArgumentException("Decryption Error: ${e.message}")
        }
    }

    // --- BASE64 HELPERS ---

    private fun stripPadding(input: String): String {
        return input.trimEnd('=')
    }

    private fun restorePadding(input: String): String {
        val missing = input.length % 4
        return if (missing > 0) {
            input + "=".repeat(4 - missing)
        } else {
            input
        }
    }

    // --- HASHING UTILITIES ---

    private fun hashSHA256(input: String): String {
        val digest = SHA256Digest()
        val inputBytes = input.toByteArray(StandardCharsets.UTF_8)
        val output = ByteArray(digest.digestSize)
        digest.update(inputBytes, 0, inputBytes.size)
        digest.doFinal(output, 0)
        return output.joinToString("") { "%02x".format(it) }
    }

    private fun hashPasswordSHA512(password: String): ByteArray {
        val digest = SHA512Digest()
        val passwordBytes = password.toByteArray(StandardCharsets.UTF_8)
        val output = ByteArray(digest.digestSize)
        digest.update(passwordBytes, 0, passwordBytes.size)
        digest.doFinal(output, 0)
        return output
    }

    // --- KEY DERIVATION ---

    private fun deriveRootSecret(sha512Password: ByteArray, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(4)
            .withMemoryPowOfTwo(16) // 64 MB
            .withParallelism(4)
            .withSalt(salt)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val result = ByteArray(32)
        generator.generateBytes(sha512Password, result, 0, 32)
        return result
    }

    private fun deriveSubKey(rootSecret: ByteArray, infoContext: String): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA512Digest())
        val infoBytes = infoContext.toByteArray(StandardCharsets.UTF_8)
        val params = HKDFParameters(rootSecret, null, infoBytes)
        hkdf.init(params)
        val subKey = ByteArray(32)
        hkdf.generateBytes(subKey, 0, 32)
        return subKey
    }

    // --- ENCRYPTION PRIMITIVES ---

    private fun encryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(true, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        return output.copyOf(len + finalLen)
    }

    private fun decryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        return output.copyOf(len + finalLen)
    }

    private fun encryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        cipher.init(true, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        return output.copyOf(len + finalLen)
    }

    private fun decryptTwofish_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        return output.copyOf(len + finalLen)
    }

    private fun encryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        cipher.init(true, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        return output.copyOf(len + finalLen)
    }

    private fun decryptSerpent_CBC(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        cipher.init(false, ParametersWithIV(KeyParameter(key), iv))
        val output = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, output, 0)
        val finalLen = cipher.doFinal(output, len)
        return output.copyOf(len + finalLen)
    }

    enum class Algorithm { AES_GCM, TWOFISH_CBC, SERPENT_CBC }
}