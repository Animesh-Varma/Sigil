package dev.animeshvarma.sigil.crypto

import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.engines.TwofishEngine
import org.bouncycastle.crypto.engines.SerpentEngine
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import java.security.SecureRandom
import java.util.Base64
import java.lang.System.currentTimeMillis
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

object CryptoEngine {

    private val secureRandom = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    // Internal Checksum Delimiter
    private const val CS_DELIMITER = "::SIGIL_CS::"

    fun encrypt(
        data: String,
        password: String,
        algorithms: List<Algorithm> = listOf(Algorithm.AES_GCM),
        logCallback: (String) -> Unit = {}
    ): String {
        val startTime = currentTimeMillis()
        logCallback("Initializing Secure Chain...")

        // 1. Generate Salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        // 2. Derive ROOT SECRET
        val sha512Password = hashPasswordSHA512(password)
        val rootSecret = deriveRootSecret(sha512Password, salt)
        logCallback("Root Secret derived.")

        // 3. Prepare Payload
        val checksum = hashSHA256(data)
        val taggedData = data + CS_DELIMITER + checksum
        var currentBytes = taggedData.toByteArray(StandardCharsets.UTF_8)
        val ivList = mutableListOf<String>()
        val algoNames = algorithms.joinToString(",") { it.name }

        logCallback("Chain Sequence: $algoNames")

        // 4. Chain Encryption
        algorithms.forEachIndexed { index, algo ->
            val layerId = index + 1
            val ivSize = if (algo == Algorithm.AES_GCM) 12 else 16
            val iv = ByteArray(ivSize).apply { secureRandom.nextBytes(this) }
            ivList.add(encoder.encodeToString(iv))

            val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId")
            logCallback("Layer $layerId: Encrypting with ${algo.name}...")

            currentBytes = when (algo) {
                Algorithm.AES_GCM -> encryptAES_GCM(currentBytes, layerKey, iv)
                Algorithm.TWOFISH_CBC -> encryptTwofish_CBC(currentBytes, layerKey, iv)
                Algorithm.SERPENT_CBC -> encryptSerpent_CBC(currentBytes, layerKey, iv)
            }
        }

        // 5. Header Encryption
        val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER")
        val metadataString = "$algoNames|${ivList.joinToString(",")}"
        val metadataBytes = metadataString.toByteArray(StandardCharsets.UTF_8)
        val headerIv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val encryptedMetadataBytes = encryptAES_GCM(metadataBytes, headerKey, headerIv)

        logCallback("Header encrypted & sealed.")

        // 6. Binary Packing
        // Layout: SALT(16) + HEADER_IV(12) + HEADER_LEN(4) + HEADER + BODY
        val packSize = 16 + 12 + 4 + encryptedMetadataBytes.size + currentBytes.size
        val packBuffer = ByteBuffer.allocate(packSize)
        packBuffer.put(salt)
        packBuffer.put(headerIv)
        packBuffer.putInt(encryptedMetadataBytes.size)
        packBuffer.put(encryptedMetadataBytes)
        packBuffer.put(currentBytes)
        val packedBytes = packBuffer.array()

        // 7. GLOBAL AUTHENTICATION (The "Encrypt-then-MAC" Fix)
        // We calculate HMAC-SHA256 of the entire packed blob
        val macKey = deriveSubKey(rootSecret, "SIGIL_GLOBAL_MAC")
        val hmac = calculateHMAC(packedBytes, macKey)

        // Final Blob: PACKED_BYTES + HMAC(32)
        val finalBuffer = ByteBuffer.allocate(packedBytes.size + hmac.size)
        finalBuffer.put(packedBytes)
        finalBuffer.put(hmac)

        val finalBytes = finalBuffer.array()
        logCallback("Global HMAC Signature applied.")

        // 8. Encode
        val finalOutput = stripPadding(encoder.encodeToString(finalBytes))
        logCallback("Operation complete in ${currentTimeMillis() - startTime}ms.")

        return finalOutput
    }

    fun decrypt(
        encryptedData: String,
        password: String,
        logCallback: (String) -> Unit = {}
    ): String {
        logCallback("Reading Secure Container...")

        val cleanData = encryptedData.filter { !it.isWhitespace() }

        try {
            val totalBytes = decoder.decode(restorePadding(cleanData))
            if (totalBytes.size < 64) throw IllegalArgumentException("Data too short/corrupted.")

            // 1. Extract Components for HMAC Verification
            // The HMAC is the last 32 bytes
            val payloadSize = totalBytes.size - 32
            val payloadBytes = ByteArray(payloadSize)
            val storedMac = ByteArray(32)

            System.arraycopy(totalBytes, 0, payloadBytes, 0, payloadSize)
            System.arraycopy(totalBytes, payloadSize, storedMac, 0, 32)

            // 2. Extract Salt (First 16 bytes of payload) needed for Root Secret
            val salt = ByteArray(16)
            System.arraycopy(payloadBytes, 0, salt, 0, 16)

            // 3. Reconstruct Root Secret
            val sha512Password = hashPasswordSHA512(password)
            val rootSecret = deriveRootSecret(sha512Password, salt)
            logCallback("Root Secret reconstructed.")

            // 4. VERIFY HMAC (Integrity Check)
            val macKey = deriveSubKey(rootSecret, "SIGIL_GLOBAL_MAC")
            val calculatedMac = calculateHMAC(payloadBytes, macKey)

            if (!Arrays.equals(storedMac, calculatedMac)) {
                logCallback("CRITICAL ERROR: HMAC Verification Failed.")
                throw IllegalArgumentException("Tampered Data or Wrong Password.")
            }
            logCallback("Global HMAC Verified. Container is authentic.")

            // 5. Unpack Structure
            val buffer = ByteBuffer.wrap(payloadBytes)
            buffer.position(16) // Skip Salt

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

            // 6. Decrypt Header
            val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER")
            val metadataBytes = decryptAES_GCM(encryptedMetadata, headerKey, headerIv)

            val metadataString = String(metadataBytes, StandardCharsets.UTF_8)
            val metaParts = metadataString.split("|")
            val algoNames = metaParts[0].split(",")
            val ivStrings = metaParts[1].split(",")

            logCallback("Layers detected: ${metaParts[0]}")

            // 7. Decrypt Body
            var currentBytes = bodyCiphertext
            for (i in algoNames.indices.reversed()) {
                val layerId = i + 1
                val algoName = algoNames[i]
                val iv = decoder.decode(ivStrings[i])
                val algo = Algorithm.valueOf(algoName)
                val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId")

                logCallback("Layer $layerId: Decrypting (${algoName})...")

                currentBytes = when (algo) {
                    Algorithm.AES_GCM -> decryptAES_GCM(currentBytes, layerKey, iv)
                    Algorithm.TWOFISH_CBC -> decryptTwofish_CBC(currentBytes, layerKey, iv)
                    Algorithm.SERPENT_CBC -> decryptSerpent_CBC(currentBytes, layerKey, iv)
                }
            }

            val rawResult = String(currentBytes, StandardCharsets.UTF_8)
            if (rawResult.contains(CS_DELIMITER)) {
                val plainText = rawResult.substringBeforeLast(CS_DELIMITER)
                logCallback("Integrity Verified (Internal Checksum).")
                return plainText
            } else {
                logCallback("Legacy format (No checksum).")
                return rawResult
            }

        } catch (e: Exception) {
            throw IllegalArgumentException("Decryption Error: ${e.message}")
        }
    }

    // --- HMAC UTILITIES ---
    private fun calculateHMAC(data: ByteArray, key: ByteArray): ByteArray {
        val hmac = HMac(SHA256Digest())
        hmac.init(KeyParameter(key))
        val output = ByteArray(hmac.macSize)
        hmac.update(data, 0, data.size)
        hmac.doFinal(output, 0)
        return output
    }

    // --- HELPERS (Unchanged) ---
    private fun stripPadding(input: String) = input.trimEnd('=')
    private fun restorePadding(input: String): String {
        val missing = input.length % 4
        return if (missing > 0) input + "=".repeat(4 - missing) else input
    }
    private fun hashSHA256(input: String): String {
        val digest = SHA256Digest()
        val b = input.toByteArray(StandardCharsets.UTF_8)
        val o = ByteArray(digest.digestSize)
        digest.update(b, 0, b.size)
        digest.doFinal(o, 0)
        return o.joinToString("") { "%02x".format(it) }
    }
    private fun hashPasswordSHA512(p: String): ByteArray {
        val d = SHA512Digest()
        val b = p.toByteArray(StandardCharsets.UTF_8)
        val o = ByteArray(d.digestSize)
        d.update(b, 0, b.size)
        d.doFinal(o, 0)
        return o
    }
    private fun deriveRootSecret(p: ByteArray, s: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(4).withMemoryPowOfTwo(16).withParallelism(4).withSalt(s).build()
        val g = Argon2BytesGenerator()
        g.init(params)
        val r = ByteArray(32)
        g.generateBytes(p, r, 0, 32)
        return r
    }
    private fun deriveSubKey(root: ByteArray, ctx: String): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA512Digest())
        hkdf.init(HKDFParameters(root, null, ctx.toByteArray(StandardCharsets.UTF_8)))
        val k = ByteArray(32)
        hkdf.generateBytes(k, 0, 32)
        return k
    }

    // --- PRIMITIVES (Unchanged) ---
    private fun encryptAES_GCM(d: ByteArray, k: ByteArray, i: ByteArray): ByteArray {
        val c = GCMBlockCipher.newInstance(AESEngine.newInstance())
        c.init(true, ParametersWithIV(KeyParameter(k), i))
        val o = ByteArray(c.getOutputSize(d.size))
        val l = c.processBytes(d, 0, d.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }
    private fun decryptAES_GCM(d: ByteArray, k: ByteArray, i: ByteArray): ByteArray {
        val c = GCMBlockCipher.newInstance(AESEngine.newInstance())
        c.init(false, ParametersWithIV(KeyParameter(k), i))
        val o = ByteArray(c.getOutputSize(d.size))
        val l = c.processBytes(d, 0, d.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }
    private fun encryptTwofish_CBC(d: ByteArray, k: ByteArray, i: ByteArray): ByteArray {
        val c = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        c.init(true, ParametersWithIV(KeyParameter(k), i))
        val o = ByteArray(c.getOutputSize(d.size))
        val l = c.processBytes(d, 0, d.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }
    private fun decryptTwofish_CBC(d: ByteArray, k: ByteArray, i: ByteArray): ByteArray {
        val c = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(TwofishEngine()), PKCS7Padding())
        c.init(false, ParametersWithIV(KeyParameter(k), i))
        val o = ByteArray(c.getOutputSize(d.size))
        val l = c.processBytes(d, 0, d.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }
    private fun encryptSerpent_CBC(d: ByteArray, k: ByteArray, i: ByteArray): ByteArray {
        val c = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        c.init(true, ParametersWithIV(KeyParameter(k), i))
        val o = ByteArray(c.getOutputSize(d.size))
        val l = c.processBytes(d, 0, d.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }
    private fun decryptSerpent_CBC(d: ByteArray, k: ByteArray, i: ByteArray): ByteArray {
        val c = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(SerpentEngine()), PKCS7Padding())
        c.init(false, ParametersWithIV(KeyParameter(k), i))
        val o = ByteArray(c.getOutputSize(d.size))
        val l = c.processBytes(d, 0, d.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }

    enum class Algorithm { AES_GCM, TWOFISH_CBC, SERPENT_CBC }
}