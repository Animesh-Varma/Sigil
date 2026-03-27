package dev.animeshvarma.sigil.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.macs.HMac
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.ParametersWithIV
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.engines.*
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    private val secureRandom = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    // Safety Limits
    private const val MAX_DATA_LIMIT = 10 * 1024 * 1024 // 10MB
    private const val MAX_METADATA_SIZE = 4096 // 4KB

    data class KdfConfig(
        val iterations: Int,
        val memoryPow2: Int,
        val parallelism: Int
    )

    enum class Algorithm {
        AES_GCM, CHACHA20_POLY1305, XCHACHA20_POLY1305,
        ARIA_256_GCM, CAMELLIA_GCM, SERPENT_GCM, TWOFISH_GCM, SM4_GCM,
        AES_CBC, TWOFISH_CBC, SERPENT_CBC, CAMELLIA_CBC,
        CAST6_CBC, RC6_CBC, SM4_CBC, SEED_CBC,
        BLOWFISH_CBC, IDEA_CBC, CAST5_CBC, TEA_CBC, XTEA_CBC, GOST_CBC
    }

    fun isAEAD(algo: Algorithm): Boolean {
        return when (algo) {
            Algorithm.AES_GCM,
            Algorithm.CHACHA20_POLY1305,
            Algorithm.XCHACHA20_POLY1305,
            Algorithm.ARIA_256_GCM,
            Algorithm.CAMELLIA_GCM,
            Algorithm.SERPENT_GCM,
            Algorithm.TWOFISH_GCM,
            Algorithm.SM4_GCM -> true
            else -> false
        }
    }

    private fun getIvSize(algo: Algorithm): Int {
        return when (algo) {
            Algorithm.AES_GCM, Algorithm.CHACHA20_POLY1305,
            Algorithm.ARIA_256_GCM, Algorithm.CAMELLIA_GCM,
            Algorithm.SERPENT_GCM, Algorithm.TWOFISH_GCM, Algorithm.SM4_GCM -> 12

            Algorithm.XCHACHA20_POLY1305 -> 24

            Algorithm.BLOWFISH_CBC, Algorithm.IDEA_CBC, Algorithm.CAST5_CBC,
            Algorithm.TEA_CBC, Algorithm.XTEA_CBC, Algorithm.GOST_CBC -> 8

            else -> 16
        }
    }

    private fun is64BitBlockCipher(algo: Algorithm): Boolean {
        return when (algo) {
            Algorithm.BLOWFISH_CBC, Algorithm.IDEA_CBC, Algorithm.CAST5_CBC,
            Algorithm.TEA_CBC, Algorithm.XTEA_CBC, Algorithm.GOST_CBC -> true
            else -> false
        }
    }

    private fun getKeySize(algo: Algorithm): Int {
        return when (algo) {
            Algorithm.SM4_CBC, Algorithm.SM4_GCM, Algorithm.SEED_CBC, Algorithm.CAST5_CBC,
            Algorithm.IDEA_CBC, Algorithm.TEA_CBC, Algorithm.XTEA_CBC, Algorithm.BLOWFISH_CBC -> 16
            else -> 32
        }
    }

    /**
     * Encrypts a byte array with a layered sequence of authenticated ciphers and returns a compact Base64 string.
     *
     * Applies Argon2id-based key derivation from the provided password and a random salt, optionally compresses
     * the plaintext, encrypts the payload through the given algorithm chain (in order), stores per-layer IVs and
     * metadata in an AES-GCM-protected header, and appends a global HMAC. The final result is Base64-encoded with
     * padding removed.
     *
     * @param data Plaintext bytes to encrypt (must be ≤ 10 MB).
     * @param password Password characters used to derive keys; the implementation clears this buffer after use.
     * @param algorithms Ordered list of algorithms to apply as layered encryption (default: AES_GCM).
     * @param kdfConfig Argon2id parameters (iterations, memoryPow2, parallelism) used for key derivation.
     * @param compress If true, compresses the plaintext before encryption.
     * @param logCallback Optional callback that receives progress, warning, and timing messages.
     * @return A Base64-encoded string (padding stripped) containing salt, encrypted header, ciphertext, and HMAC.
     * @throws IllegalArgumentException If input, intermediate pack, or final output sizes exceed configured safety limits.
     */
    fun encrypt(
        data: ByteArray,
        password: CharArray,
        algorithms: List<Algorithm> = listOf(Algorithm.AES_GCM),
        kdfConfig: KdfConfig,
        compress: Boolean = true,
        logCallback: (String) -> Unit = {}
    ): String {
        require(data.size <= MAX_DATA_LIMIT) { "Input exceeds 10MB safety limit." }

        val startTime = System.currentTimeMillis()
        logCallback("Initializing Secure Chain...")

        // 1. Salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        // 2. Root Secret (Argon2id)
        val passBytes = toBytes(password)
        val rootSecret = try {
            deriveKeyArgon2(passBytes, salt, kdfConfig, 32)
        } finally {
            passBytes.fill(0.toByte())
        }

        logCallback("Root Secret derived (Argon2id: ${kdfConfig.memoryPow2} pow2 memory, ${kdfConfig.iterations} iterations).")

        // 3. Payload
        var currentBytes = data.copyOf()

        if (compress) {
            val originalSize = currentBytes.size
            currentBytes = compressData(currentBytes)
            logCallback("Compression: $originalSize -> ${currentBytes.size} bytes")
        }

        // 4. Chain Encryption
        val ivListBytes = mutableListOf<ByteArray>()
        val algoNames = algorithms.joinToString(",") { it.name }

        logCallback("Chain Sequence: $algoNames")

        algorithms.forEachIndexed { index, algo ->
            val layerId = index + 1
            if (is64BitBlockCipher(algo)) {
                logCallback("[WARNING] Layer $layerId uses ${algo.name} (64-bit block - Weak).")
            }

            val ivSize = getIvSize(algo)
            val iv = ByteArray(ivSize).apply { secureRandom.nextBytes(this) }
            ivListBytes.add(iv)

            val layerKey = deriveSubKey(rootSecret, salt, "SIGIL_LAYER_$layerId", getKeySize(algo))

            logCallback("Layer $layerId: Encrypting with ${algo.name}...")
            currentBytes = processCipher(true, algo, currentBytes, layerKey, iv)
            layerKey.fill(0.toByte())
        }

        // 5. Header
        val headerKey = deriveSubKey(rootSecret, salt, "SIGIL_HEADER", 32)
        val flags = if (compress) "C" else "N"
        val ivStringList = ivListBytes.joinToString(",") { encoder.encodeToString(it) }

        val metadataString = "$algoNames|$ivStringList|$flags"
        val metadataBytes = metadataString.toByteArray(StandardCharsets.UTF_8)
        val headerIv = ByteArray(12).apply { secureRandom.nextBytes(this) }

        val encryptedMetadataBytes = encryptAesGcm(metadataBytes, headerKey, headerIv, salt)
        headerKey.fill(0.toByte())

        logCallback("Header encrypted (AES-GCM + AAD).")

        // 6. Pack
        val totalSize = 16 + 12 + 4 + encryptedMetadataBytes.size + currentBytes.size
        require(totalSize <= MAX_DATA_LIMIT) { "Pack size exceeds limit." }

        val packBuffer = ByteBuffer.allocate(totalSize)
        packBuffer.put(salt)
        packBuffer.put(headerIv)
        packBuffer.putInt(encryptedMetadataBytes.size)
        packBuffer.put(encryptedMetadataBytes)
        packBuffer.put(currentBytes)
        val packedBytes = packBuffer.array()

        // 7. MAC
        val macKey = deriveSubKey(rootSecret, salt, "SIGIL_GLOBAL_MAC", 32)
        val hmac = calculateHMAC(packedBytes, macKey)
        macKey.fill(0.toByte())
        rootSecret.fill(0.toByte())

        val finalBytes = ByteBuffer.allocate(packedBytes.size + hmac.size)
            .put(packedBytes).put(hmac).array()

        require(finalBytes.size <= MAX_DATA_LIMIT) { "Output exceeds limit." }

        logCallback("Global HMAC Signature applied.")
        logCallback("Operation complete in ${System.currentTimeMillis() - startTime}ms.")

        return stripPadding(encoder.encodeToString(finalBytes))
    }

    /**
     * Decrypts a secure container produced by this engine and returns the original plaintext bytes.
     *
     * Performs Argon2id key derivation using the provided password and kdfConfig, verifies a global
     * HMAC to ensure integrity, decrypts the layered cipher sequence stored in the container, and
     * optionally decompresses the final payload when indicated by the metadata. Sensitive keying
     * material derived during the operation is cleared from memory before returning.
     *
     * @param encryptedData Base64-encoded container string produced by the corresponding encrypt routine.
     * @param password The password as a mutable CharArray; contents are zeroed after use.
     * @param kdfConfig Argon2id parameter set used to reconstruct key material.
     * @return The decrypted plaintext bytes.
     * @throws IllegalArgumentException If decryption fails for any reason (integrity check, malformed
     *     input, wrong password, or other errors). The exception message is intentionally generic to
     *     avoid leaking sensitive information.
     */
    fun decrypt(
        encryptedData: String,
        password: CharArray,
        kdfConfig: KdfConfig,
        logCallback: (String) -> Unit = {}
    ): ByteArray {
        val startTime = System.currentTimeMillis()
        logCallback("Reading Secure Container...")

        val genericError = "Decryption failed. Verify password and data integrity."

        var rootSecret: ByteArray? = null

        try {
            val cleanData = encryptedData.filter { !it.isWhitespace() }
            val totalBytes = decoder.decode(restorePadding(cleanData))

            if (totalBytes.size < 64) throw Exception()
            if (totalBytes.size > MAX_DATA_LIMIT) throw Exception()

            // 1. HMAC Check
            val payloadSize = totalBytes.size - 32
            val payloadBytes = ByteArray(payloadSize)
            val storedMac = ByteArray(32)

            totalBytes.copyInto(payloadBytes, 0, 0, payloadSize)
            totalBytes.copyInto(storedMac, 0, payloadSize, totalBytes.size)

            val salt = ByteArray(16)
            payloadBytes.copyInto(salt, 0, 0, 16)

            // Reconstruct Root
            val passBytes = toBytes(password)
            rootSecret = try {
                deriveKeyArgon2(passBytes, salt, kdfConfig, 32)
            } finally {
                passBytes.fill(0.toByte())
            }
            logCallback("Root Secret reconstructed (Argon2id: ${kdfConfig.memoryPow2} pow2 memory, ${kdfConfig.iterations} iterations).")

            val macKey = deriveSubKey(rootSecret, salt, "SIGIL_GLOBAL_MAC", 32)
            val calculatedMac = calculateHMAC(payloadBytes, macKey)

            val validMac = constantTimeEquals(storedMac, calculatedMac)
            macKey.fill(0.toByte())

            if (!validMac) throw Exception()
            logCallback("Global HMAC Verified.")

            // 2. Unpack
            val buffer = ByteBuffer.wrap(payloadBytes)
            buffer.position(16) // Skip Salt
            val headerIv = ByteArray(12); buffer.get(headerIv)
            val headerLen = buffer.int

            if (headerLen !in 0..MAX_METADATA_SIZE) throw Exception()

            val encryptedMeta = ByteArray(headerLen); buffer.get(encryptedMeta)
            val bodyBytes = ByteArray(buffer.remaining()); buffer.get(bodyBytes)

            // 3. Header
            val headerKey = deriveSubKey(rootSecret, salt, "SIGIL_HEADER", 32)
            val metaBytes = decryptAesGcm(encryptedMeta, headerKey, headerIv, salt)
            headerKey.fill(0.toByte())
            logCallback("Header decrypted.")

            val metaString = String(metaBytes, StandardCharsets.UTF_8)
            val parts = metaString.split("|")
            val algoNames = parts[0].split(",")
            val ivStrings = parts[1].split(",")
            val isCompressed = parts.getOrNull(2) == "C"

            logCallback("Chain Sequence detected: ${parts[0]}")

            // 4. Decrypt Chain
            var currentBytes = bodyBytes
            for (i in algoNames.indices.reversed()) {
                val layerId = i + 1
                val algo = Algorithm.valueOf(algoNames[i])
                val iv = decoder.decode(ivStrings[i])
                val keySize = getKeySize(algo)

                if (is64BitBlockCipher(algo)) {
                    logCallback("[WARNING] Layer $layerId uses ${algo.name} (64-bit block - Weak).")
                }
                logCallback("Layer $layerId: Decrypting with ${algo.name}...")
                val layerKey = deriveSubKey(rootSecret, salt, "SIGIL_LAYER_$layerId", keySize)
                currentBytes = processCipher(false, algo, currentBytes, layerKey, iv)
                layerKey.fill(0.toByte())
            }

            // 5. Decompress
            if (isCompressed) {
                val compressedSize = currentBytes.size
                currentBytes = safeDecompress(currentBytes)
                logCallback("Decompressed: $compressedSize -> ${currentBytes.size} bytes")
            }

            logCallback("Decryption complete in ${System.currentTimeMillis() - startTime}ms.")
            return currentBytes

        } catch (_: Exception) {
            rootSecret?.fill(0.toByte())
            throw IllegalArgumentException(genericError)
        } finally {
            rootSecret?.fill(0.toByte())
        }
    }

    /**
     * Encrypts a single byte payload with the specified algorithm and returns a compact raw container.
     *
     * The returned container is Base64-encoded and contains, in order: 16-byte salt, IV (algorithm-dependent size),
     * and the ciphertext produced by the chosen algorithm.
     *
     * **SECURITY WARNING:** Unlike the standard [encrypt] method, this function does NOT apply a global HMAC.
     * If [algorithm] is not an AEAD cipher (e.g. CBC modes), the resulting ciphertext lacks integrity protection.
     * Malicious modification of the ciphertext will not be detected during decryption. This is intended for educational purposes.
     *
     * @param data Plaintext bytes to encrypt (must be <= 10MB).
     * @param password Password as a CharArray; its UTF-8 bytes are derived and cleared after use.
     * @param algorithm Cipher algorithm to use for encryption and IV/key sizing.
     * @param kdfConfig Argon2id parameters used to derive the encryption key.
     * @param logCallback Optional logging function receiving progress/log messages.
     * @return A Base64-encoded string containing salt || iv || ciphertext.
     * @throws IllegalArgumentException If `data` exceeds the 10MB safety limit.
     */
    fun encryptRaw(
        data: ByteArray,
        password: CharArray,
        algorithm: Algorithm,
        kdfConfig: KdfConfig,
        logCallback: (String) -> Unit = {}
    ): String {
        require(data.size <= MAX_DATA_LIMIT) { "Input exceeds 10MB safety limit." }

        val startTime = System.currentTimeMillis()
        logCallback("Starting Raw Encryption (${algorithm.name})...")

        // 1. Generate Salt
        val salt = ByteArray(16).apply { secureRandom.nextBytes(this) }

        // 2. Generate IV (Dynamic Size)
        val ivSize = getIvSize(algorithm)
        val iv = ByteArray(ivSize).apply { secureRandom.nextBytes(this) }

        // 3. Derive Key (Argon2id direct to Key Size)
        val keySize = getKeySize(algorithm)
        val passBytes = toBytes(password)
        val key = try {
            deriveKeyArgon2(passBytes, salt, kdfConfig, keySize)
        } finally {
            passBytes.fill(0.toByte())
        }

        logCallback("Key derived (Argon2id).")

        // 4. Encrypt using generalized processor
        val ciphertext = try {
            processCipher(true, algorithm, data, key, iv)
        } finally {
            key.fill(0.toByte())
        }

        // 5. Pack (Salt + IV + Ciphertext)
        val output = ByteBuffer.allocate(salt.size + iv.size + ciphertext.size)
            .put(salt)
            .put(iv)
            .put(ciphertext)
            .array()

        require(output.size <= MAX_DATA_LIMIT) { "Output exceeds 10MB safety limit." }

        logCallback("Raw Encryption complete in ${System.currentTimeMillis() - startTime}ms.")

        return encoder.encodeToString(output)
    }

    /**
     * Decrypts a standalone raw container produced by encryptRaw.
     *
     * **SECURITY WARNING:** If the data was encrypted using a non-AEAD algorithm (e.g. CBC modes),
     * this method cannot verify the integrity of the ciphertext. Tampered data may result in garbage
     * output rather than a validation error. This is intended for educational purposes.
     *
     * @param encryptedData Base64-encoded container string (whitespace allowed) containing salt, IV, and ciphertext.
     * @param password Password as a CharArray used to derive the decryption key; the function clears this input internally.
     * @param algorithm The Algorithm that was used to encrypt the container.
     * @param kdfConfig Argon2id parameters used for key derivation.
     * @param logCallback Optional logging function that receives progress messages.
     * @return The decrypted plaintext bytes.
     * @throws IllegalArgumentException If decoding, key derivation, or decryption fails (e.g., wrong password/profile or data corruption).
     */
    fun decryptRaw(
        encryptedData: String,
        password: CharArray,
        algorithm: Algorithm,
        kdfConfig: KdfConfig,
        logCallback: (String) -> Unit = {}
    ): ByteArray {
        val startTime = System.currentTimeMillis()
        logCallback("Reading Raw Container...")

        try {
            // 1. Decode
            val cleanData = encryptedData.filter { !it.isWhitespace() }
            val rawBytes = decoder.decode(cleanData)

            if (rawBytes.size > MAX_DATA_LIMIT) throw IllegalArgumentException("Input exceeds 10MB safety limit.")

            val ivSize = getIvSize(algorithm)
            val minSize = 16 + ivSize // Salt + IV
            if (rawBytes.size <= minSize) throw IllegalArgumentException("Invalid data size.")

            // 2. Unpack
            val buffer = ByteBuffer.wrap(rawBytes)
            val salt = ByteArray(16); buffer.get(salt)
            val iv = ByteArray(ivSize); buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining()); buffer.get(ciphertext)

            // 3. Derive Key
            val keySize = getKeySize(algorithm)
            val passBytes = toBytes(password)
            val key = try {
                deriveKeyArgon2(passBytes, salt, kdfConfig, keySize)
            } finally {
                passBytes.fill(0.toByte())
            }

            // 4. Decrypt
            val plaintext = try {
                processCipher(false, algorithm, ciphertext, key, iv)
            } finally {
                key.fill(0.toByte())
            }

            logCallback("Decryption complete in ${System.currentTimeMillis() - startTime}ms.")
            return plaintext

        } catch (_: Exception) {
            throw IllegalArgumentException("Raw Decryption failed. Wrong password, profile, or data corruption.")
        }
    }

    /**
     * Derives a 32-byte Argon2id hash from a PIN and salt.
     *
     * The function uses fixed Argon2id parameters to produce a 32-byte key from the provided PIN and salt.
     *
     * @param pin The PIN to hash; the function will clear the PIN contents after use.
     * @param salt The salt bytes to use for derivation.
     * @return A 32-byte derived hash.
     */
    fun hashPin(pin: CharArray, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(5)
            .withMemoryPowOfTwo(16)
            .withParallelism(2)
            .withSalt(salt)
            .build()

        val g = Argon2BytesGenerator()
        g.init(params)
        val pinBytes = toBytes(pin)
        val hash = ByteArray(32)
        try {
            g.generateBytes(pinBytes, hash, 0, hash.size)
            return hash
        } finally {
            pinBytes.fill(0.toByte())
        }
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }

    private fun toBytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = byteBuffer.array().copyOfRange(byteBuffer.position(), byteBuffer.limit())
        byteBuffer.array().fill(0.toByte())
        return bytes
    }

    private fun compressData(data: ByteArray): ByteArray {
        require(data.size <= MAX_DATA_LIMIT) { "Input too large." }
        val d = Deflater(Deflater.BEST_COMPRESSION)
        d.setInput(data)
        d.finish()
        val o = ByteArrayOutputStream(data.size)
        val b = ByteArray(1024)
        while (!d.finished()) {
            val c = d.deflate(b)
            o.write(b, 0, c)
            require(o.size() <= MAX_DATA_LIMIT) { "Compression overflow." }
        }
        d.end()
        return o.toByteArray()
    }

    private fun safeDecompress(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        var totalBytes = 0
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0 && !inflater.finished()) break
            totalBytes += count
            require(totalBytes <= MAX_DATA_LIMIT) { "Decompression Limit Exceeded." }
            outputStream.write(buffer, 0, count)
        }
        inflater.end()
        return outputStream.toByteArray()
    }

    /**
     * Removes Base64 padding characters from the end of a string.
     *
     * @param i The input string (typically Base64-encoded) from which trailing `=` characters should be removed.
     * @return The input string with all trailing `=` characters removed.
     */
    private fun stripPadding(i: String) = i.trimEnd('=')
    /**
     * Restores Base64 padding so the input's length becomes a multiple of four.
     *
     * @param i A Base64-encoded string that may have had trailing '=' padding removed.
     * @return The input string with '=' characters appended as needed to reach a length divisible by 4.
     */
    private fun restorePadding(i: String): String { val m = i.length % 4; return if (m > 0) i + "=".repeat(4 - m) else i }
    /**
     * Derives a fixed-length key from password bytes and a salt using Argon2id with the provided KDF parameters.
     *
     * Uses Argon2id (version 1.3) and the iterations, memory, and parallelism from [config] to produce a key of the requested length.
     *
     * @param p Password-derived input bytes (will be used as Argon2 input).
     * @param s Salt bytes to use for derivation.
     * @param config Argon2id parameter set (iterations, memoryPow2, parallelism).
     * @param length Desired length of the derived key in bytes.
     * @return A byte array of size `length` containing the derived key.
     */
    private fun deriveKeyArgon2(p: ByteArray, s: ByteArray, config: KdfConfig, length: Int): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(config.iterations)
            .withMemoryPowOfTwo(config.memoryPow2)
            .withParallelism(config.parallelism)
            .withSalt(s)
            .build()
        val g = Argon2BytesGenerator()
        g.init(params)
        val r = ByteArray(length)
        g.generateBytes(p, r, 0, length)
        return r
    }

    private fun deriveSubKey(r: ByteArray, salt: ByteArray, c: String, l: Int): ByteArray {
        val h = HKDFBytesGenerator(SHA512Digest())
        h.init(HKDFParameters(r, salt, c.toByteArray(StandardCharsets.UTF_8)))
        val k = ByteArray(l)
        h.generateBytes(k, 0, l)
        return k
    }

    private fun calculateHMAC(d: ByteArray, k: ByteArray): ByteArray { val h = HMac(SHA256Digest()); h.init(KeyParameter(k)); val o = ByteArray(h.macSize); h.update(d, 0, d.size); h.doFinal(o, 0); return o }

    private fun processCipher(encrypt: Boolean, algo: Algorithm, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return when (algo) {
            Algorithm.AES_GCM -> if (encrypt) encryptAesGcm(data, key, iv, null) else decryptAesGcm(data, key, iv, null)
            Algorithm.CHACHA20_POLY1305 -> processChaCha20Poly1305(encrypt, data, key, iv)
            Algorithm.XCHACHA20_POLY1305 -> processXChaCha20Poly1305(encrypt, data, key, iv)

            // --- GCM MAPPED ALGORITHMS ---
            Algorithm.ARIA_256_GCM -> processGcmBlockCipher(encrypt, ARIAEngine(), data, key, iv)
            Algorithm.CAMELLIA_GCM -> processGcmBlockCipher(encrypt, CamelliaEngine(), data, key, iv)
            Algorithm.SERPENT_GCM -> processGcmBlockCipher(encrypt, SerpentEngine(), data, key, iv)
            Algorithm.TWOFISH_GCM -> processGcmBlockCipher(encrypt, TwofishEngine(), data, key, iv)
            Algorithm.SM4_GCM -> processGcmBlockCipher(encrypt, SM4Engine(), data, key, iv)

            // --- CBC MAPPED ALGORITHMS ---
            Algorithm.AES_CBC -> processBlockCipher(encrypt, AESEngine.newInstance(), data, key, iv)
            Algorithm.TWOFISH_CBC -> processBlockCipher(encrypt, TwofishEngine(), data, key, iv)
            Algorithm.SERPENT_CBC -> processBlockCipher(encrypt, SerpentEngine(), data, key, iv)
            Algorithm.CAMELLIA_CBC -> processBlockCipher(encrypt, CamelliaEngine(), data, key, iv)
            Algorithm.CAST6_CBC -> processBlockCipher(encrypt, CAST6Engine(), data, key, iv)
            Algorithm.RC6_CBC -> processBlockCipher(encrypt, RC6Engine(), data, key, iv)
            Algorithm.SM4_CBC -> processBlockCipher(encrypt, SM4Engine(), data, key, iv)
            Algorithm.SEED_CBC -> processBlockCipher(encrypt, SEEDEngine(), data, key, iv)
            Algorithm.BLOWFISH_CBC -> processBlockCipher(encrypt, BlowfishEngine(), data, key, iv)
            Algorithm.IDEA_CBC -> processBlockCipher(encrypt, IDEAEngine(), data, key, iv)
            Algorithm.CAST5_CBC -> processBlockCipher(encrypt, CAST5Engine(), data, key, iv)
            Algorithm.GOST_CBC -> processBlockCipher(encrypt, GOST28147Engine(), data, key, iv)
            Algorithm.TEA_CBC -> processBlockCipher(encrypt, TEAEngine(), data, key, iv)
            Algorithm.XTEA_CBC -> processBlockCipher(encrypt, XTEAEngine(), data, key, iv)
        }
    }

    // --- LOW LEVEL CIPHER IMPLEMENTATIONS ---

    // Generic wrapper for any 128-bit BouncyCastle Engine to act as GCM/AEAD
    private fun processGcmBlockCipher(encrypt: Boolean, engine: org.bouncycastle.crypto.BlockCipher, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = GCMBlockCipher.newInstance(engine, org.bouncycastle.crypto.modes.gcm.BasicGCMMultiplier())
        // 128 is the MAC length in bits
        val params = AEADParameters(KeyParameter(key), 128, iv, null)
        cipher.init(encrypt, params)
        val out = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, out, 0)
        val finalLen = cipher.doFinal(out, len)
        return out.copyOf(len + finalLen)
    }

    private fun processChaCha20Poly1305(encrypt: Boolean, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        val params = AEADParameters(KeyParameter(key), 128, iv, null)
        cipher.init(encrypt, params)
        val out = ByteArray(cipher.getOutputSize(data.size))
        val len = cipher.processBytes(data, 0, data.size, out, 0)
        val finalLen = cipher.doFinal(out, len)
        return out.copyOf(len + finalLen)
    }

    private fun processXChaCha20Poly1305(encrypt: Boolean, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        require(iv.size == 24) { "XChaCha20 requires 24-byte nonce" }

        // 1. Split IV into nonce (16 bytes) and block counter/remaining nonce (8 bytes)
        val hNonce = iv.copyOfRange(0, 16)
        val cNonce = iv.copyOfRange(16, 24)

        // 2. HChaCha20 Derivation
        val subKey = hChaCha20(key, hNonce)

        // 3. Construct 12-byte IV for ChaCha20 (4 bytes 0 + 8 bytes cNonce)
        val subIv = ByteArray(12)
        System.arraycopy(cNonce, 0, subIv, 4, 8)

        // 4. Delegate to standard ChaCha20Poly1305
        return try {
            processChaCha20Poly1305(encrypt, data, subKey, subIv)
        } finally {
            subKey.fill(0.toByte())
        }
    }

    private fun processBlockCipher(encrypt: Boolean, engine: org.bouncycastle.crypto.BlockCipher, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(engine), PKCS7Padding())
        cipher.init(encrypt, ParametersWithIV(KeyParameter(key), iv))
        val out = ByteArray(cipher.getOutputSize(data.size))
        val l = cipher.processBytes(data, 0, data.size, out, 0)
        val f = cipher.doFinal(out, l)
        return out.copyOf(l + f)
    }

    private fun encryptAesGcm(data: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(data)
    }

    private fun decryptAesGcm(data: ByteArray, key: ByteArray, iv: ByteArray, aad: ByteArray?): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keySpec = SecretKeySpec(key, "AES")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        if (aad != null) cipher.updateAAD(aad)
        return cipher.doFinal(data)
    }

    private fun hChaCha20(key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == 32) { "HChaCha20 requires 32-byte key" }
        require(nonce.size == 16) { "HChaCha20 requires 16-byte nonce" }
        val state = IntArray(16)

        try {
            // Constants (expand 32-byte k)
            state[0] = 0x61707865; state[1] = 0x3320646e; state[2] = 0x79622d32; state[3] = 0x6b206574

            // Key
            val k = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
            for (i in 0..7) state[4 + i] = k[i]

            // Nonce
            val n = ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
            for (i in 0..3) state[12 + i] = n[i]

            // 20 Rounds
            repeat(10) {
                quarterRound(state, 0, 4, 8, 12)
                quarterRound(state, 1, 5, 9, 13)
                quarterRound(state, 2, 6, 10, 14)
                quarterRound(state, 3, 7, 11, 15)
                quarterRound(state, 0, 5, 10, 15)
                quarterRound(state, 1, 6, 11, 12)
                quarterRound(state, 2, 7, 8, 13)
                quarterRound(state, 3, 4, 9, 14)
            }

            // Output: State words 0-3 and 12-15
            val out = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0..3) out.putInt(state[i])
            for (i in 12..15) out.putInt(state[i])
            return out.array()

        } finally {
            state.fill(0)
        }
    }

    private fun quarterRound(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = rotateLeft(x[d] xor x[a], 16)
        x[c] += x[d]; x[b] = rotateLeft(x[b] xor x[c], 12)
        x[a] += x[b]; x[d] = rotateLeft(x[d] xor x[a], 8)
        x[c] += x[d]; x[b] = rotateLeft(x[b] xor x[c], 7)
    }

    private fun rotateLeft(i: Int, distance: Int): Int {
        return (i shl distance) or (i ushr -distance)
    }
}