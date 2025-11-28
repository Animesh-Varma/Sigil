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
import org.bouncycastle.crypto.engines.*
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.modes.CBCBlockCipher
import org.bouncycastle.crypto.paddings.PKCS7Padding
import org.bouncycastle.crypto.paddings.PaddedBufferedBlockCipher
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import java.util.Arrays
import java.util.zip.Deflater
import java.util.zip.Inflater

object CryptoEngine {

    private val secureRandom = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()
    private const val CS_DELIMITER = "::SIGIL_CS::"

    enum class Algorithm {
        AES_GCM, AES_CBC, TWOFISH_CBC, SERPENT_CBC, CAMELLIA_CBC, CAST6_CBC,
        RC6_CBC, BLOWFISH_CBC, IDEA_CBC, CAST5_CBC, SM4_CBC, GOST_CBC, SEED_CBC, TEA_CBC, XTEA_CBC
    }

    fun encrypt(
        data: String,
        password: String,
        algorithms: List<Algorithm> = listOf(Algorithm.AES_GCM),
        compress: Boolean = true, // [NEW] Compression Toggle
        logCallback: (String) -> Unit = {}
    ): String {
        val startTime = java.lang.System.currentTimeMillis()
        logCallback("Initializing Secure Chain...")

        // 1. Salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        // 2. Root Secret
        val sha512Password = hashPasswordSHA512(password)
        val rootSecret = deriveRootSecret(sha512Password, salt)
        logCallback("Root Secret derived.")

        // 3. Prepare Payload (Checksum + Compression)
        val checksum = hashSHA256(data)
        val rawString = data + CS_DELIMITER + checksum
        var currentBytes = rawString.toByteArray(StandardCharsets.UTF_8)

        if (compress) {
            val originalSize = currentBytes.size
            currentBytes = compressData(currentBytes)
            logCallback("Compression: $originalSize bytes -> ${currentBytes.size} bytes")
        }

        // 4. Chain Encryption
        val ivList = mutableListOf<String>()
        val algoNames = algorithms.joinToString(",") { it.name }

        logCallback("Chain: $algoNames")

        algorithms.forEachIndexed { index, algo ->
            val layerId = index + 1
            // GCM needs 12 bytes, CBC needs 16 bytes (block size)
            val ivSize = if (algo == Algorithm.AES_GCM) 12 else 16
            val iv = ByteArray(ivSize).apply { secureRandom.nextBytes(this) }
            ivList.add(encoder.encodeToString(iv))

            val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId")
            logCallback("Layer $layerId: ${algo.name}")

            currentBytes = processCipher(true, algo, currentBytes, layerKey, iv)
        }

        // 5. Header (Now includes Compression Flag 'C')
        val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER")
        val flags = if (compress) "C" else "N"
        val metadataString = "$algoNames|${ivList.joinToString(",")}|$flags"
        val metadataBytes = metadataString.toByteArray(StandardCharsets.UTF_8)

        val headerIv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val encryptedMetadataBytes = encryptAES_GCM(metadataBytes, headerKey, headerIv)

        // 6. Pack & MAC
        val packBuffer = ByteBuffer.allocate(16 + 12 + 4 + encryptedMetadataBytes.size + currentBytes.size)
        packBuffer.put(salt)
        packBuffer.put(headerIv)
        packBuffer.putInt(encryptedMetadataBytes.size)
        packBuffer.put(encryptedMetadataBytes)
        packBuffer.put(currentBytes)
        val packedBytes = packBuffer.array()

        val macKey = deriveSubKey(rootSecret, "SIGIL_GLOBAL_MAC")
        val hmac = calculateHMAC(packedBytes, macKey)

        val finalBytes = ByteBuffer.allocate(packedBytes.size + hmac.size)
            .put(packedBytes).put(hmac).array()

        logCallback("Complete in ${java.lang.System.currentTimeMillis() - startTime}ms.")
        return stripPadding(encoder.encodeToString(finalBytes))
    }

    fun decrypt(
        encryptedData: String,
        password: String,
        logCallback: (String) -> Unit = {}
    ): String {
        logCallback("Reading Container...")
        val cleanData = encryptedData.filter { !it.isWhitespace() }

        try {
            val totalBytes = decoder.decode(restorePadding(cleanData))
            if (totalBytes.size < 64) throw IllegalArgumentException("Data corrupted.")

            // 1. HMAC Check
            val payloadSize = totalBytes.size - 32
            val payloadBytes = ByteArray(payloadSize)
            val storedMac = ByteArray(32)
            System.arraycopy(totalBytes, 0, payloadBytes, 0, payloadSize)
            System.arraycopy(totalBytes, payloadSize, storedMac, 0, 32)

            val salt = ByteArray(16)
            System.arraycopy(payloadBytes, 0, salt, 0, 16)

            val sha512Password = hashPasswordSHA512(password)
            val rootSecret = deriveRootSecret(sha512Password, salt)

            val macKey = deriveSubKey(rootSecret, "SIGIL_GLOBAL_MAC")
            if (!Arrays.equals(storedMac, calculateHMAC(payloadBytes, macKey))) {
                throw IllegalArgumentException("HMAC Verification Failed.")
            }
            logCallback("Integrity Verified.")

            // 2. Unpack
            val buffer = ByteBuffer.wrap(payloadBytes)
            buffer.position(16)
            val headerIv = ByteArray(12)
            buffer.get(headerIv)
            val headerLen = buffer.int
            val encryptedMeta = ByteArray(headerLen)
            buffer.get(encryptedMeta)
            val bodyBytes = ByteArray(buffer.remaining())
            buffer.get(bodyBytes)

            // 3. Header
            val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER")
            val metaBytes = decryptAES_GCM(encryptedMeta, headerKey, headerIv)
            val metaString = String(metaBytes, StandardCharsets.UTF_8)
            val parts = metaString.split("|")

            val algoNames = parts[0].split(",")
            val ivStrings = parts[1].split(",")
            val isCompressed = parts.getOrNull(2) == "C"

            logCallback("Layers: ${parts[0]} | Compressed: $isCompressed")

            // 4. Decrypt Chain
            var currentBytes = bodyBytes
            for (i in algoNames.indices.reversed()) {
                val layerId = i + 1
                val algo = Algorithm.valueOf(algoNames[i])
                val iv = decoder.decode(ivStrings[i])
                val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId")

                logCallback("Layer $layerId: Decrypting ${algo.name}...")
                currentBytes = processCipher(false, algo, currentBytes, layerKey, iv)
            }

            // 5. Decompress
            if (isCompressed) {
                currentBytes = decompressData(currentBytes)
                logCallback("Decompressed.")
            }

            // 6. Checksum
            val result = String(currentBytes, StandardCharsets.UTF_8)
            if (result.contains(CS_DELIMITER)) {
                val plain = result.substringBeforeLast(CS_DELIMITER)
                val sum = result.substringAfterLast(CS_DELIMITER)
                if (sum == hashSHA256(plain)) return plain
                else return "ERROR: Checksum mismatch."
            }
            return result

        } catch (e: Exception) {
            throw IllegalArgumentException(e.message)
        }
    }

    // --- GENERIC CIPHER PROCESSOR ---
    private fun processCipher(encrypt: Boolean, algo: Algorithm, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return when (algo) {
            Algorithm.AES_GCM -> if (encrypt) encryptAES_GCM(data, key, iv) else decryptAES_GCM(data, key, iv)
            // Block Ciphers (CBC)
            Algorithm.AES_CBC -> processBlockCipher(encrypt, AESEngine.newInstance(), data, key, iv)
            Algorithm.TWOFISH_CBC -> processBlockCipher(encrypt, TwofishEngine(), data, key, iv)
            Algorithm.SERPENT_CBC -> processBlockCipher(encrypt, SerpentEngine(), data, key, iv)
            Algorithm.CAMELLIA_CBC -> processBlockCipher(encrypt, CamelliaEngine(), data, key, iv)
            Algorithm.CAST6_CBC -> processBlockCipher(encrypt, CAST6Engine(), data, key, iv)
            Algorithm.RC6_CBC -> processBlockCipher(encrypt, RC6Engine(), data, key, iv)
            Algorithm.BLOWFISH_CBC -> processBlockCipher(encrypt, BlowfishEngine(), data, key, iv)
            Algorithm.IDEA_CBC -> processBlockCipher(encrypt, IDEAEngine(), data, key, iv)
            Algorithm.CAST5_CBC -> processBlockCipher(encrypt, CAST5Engine(), data, key, iv)
            Algorithm.SM4_CBC -> processBlockCipher(encrypt, SM4Engine(), data, key, iv)
            Algorithm.GOST_CBC -> processBlockCipher(encrypt, GOST28147Engine(), data, key, iv)
            Algorithm.SEED_CBC -> processBlockCipher(encrypt, SEEDEngine(), data, key, iv)
            Algorithm.TEA_CBC -> processBlockCipher(encrypt, TEAEngine(), data, key, iv)
            Algorithm.XTEA_CBC -> processBlockCipher(encrypt, XTEAEngine(), data, key, iv)
        }
    }

    // --- PRIMITIVES ---
    private fun processBlockCipher(encrypt: Boolean, engine: org.bouncycastle.crypto.BlockCipher, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = PaddedBufferedBlockCipher(CBCBlockCipher.newInstance(engine), PKCS7Padding())
        // Adjust key length if necessary (some older ciphers like DES/Blowfish handle keys differently, but HKDF 32-bytes usually works or is truncated safely by BC)
        cipher.init(encrypt, ParametersWithIV(KeyParameter(key), iv))
        val out = ByteArray(cipher.getOutputSize(data.size))
        val l = cipher.processBytes(data, 0, data.size, out, 0)
        val f = cipher.doFinal(out, l)
        return out.copyOf(l + f)
    }
    private fun encryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val c = GCMBlockCipher.newInstance(AESEngine.newInstance())
        c.init(true, ParametersWithIV(KeyParameter(key), iv))
        val o = ByteArray(c.getOutputSize(data.size))
        val l = c.processBytes(data, 0, data.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }

    private fun decryptAES_GCM(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val c = GCMBlockCipher.newInstance(AESEngine.newInstance())
        c.init(false, ParametersWithIV(KeyParameter(key), iv))
        val o = ByteArray(c.getOutputSize(data.size))
        val l = c.processBytes(data, 0, data.size, o, 0)
        val f = c.doFinal(o, l)
        return o.copyOf(l + f)
    }

    // --- COMPRESSION ---
    private fun compressData(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        outputStream.close()
        return outputStream.toByteArray()
    }

    private fun decompressData(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val outputStream = ByteArrayOutputStream(data.size)
        val buffer = ByteArray(1024)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }
        outputStream.close()
        return outputStream.toByteArray()
    }

    // --- HELPERS (Keep existing hash/derive functions) ---
    // (Pasting abbreviated versions for brevity, use previous implementations for hashSHA256, hashPasswordSHA512, deriveRootSecret, deriveSubKey, calculateHMAC, stripPadding, restorePadding)

    private fun stripPadding(input: String) = input.trimEnd('=')
    private fun restorePadding(input: String): String {
        val missing = input.length % 4
        return if (missing > 0) input + "=".repeat(4 - missing) else input
    }
    private fun hashSHA256(input: String): String {
        val d = SHA256Digest(); val b = input.toByteArray(StandardCharsets.UTF_8); val o = ByteArray(d.digestSize); d.update(b, 0, b.size); d.doFinal(o, 0); return o.joinToString("") { "%02x".format(it) }
    }
    private fun hashPasswordSHA512(p: String): ByteArray {
        val d = SHA512Digest(); val b = p.toByteArray(StandardCharsets.UTF_8); val o = ByteArray(d.digestSize); d.update(b, 0, b.size); d.doFinal(o, 0); return o
    }
    private fun deriveRootSecret(p: ByteArray, s: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13).withIterations(4).withMemoryPowOfTwo(16).withParallelism(4).withSalt(s).build()
        val g = Argon2BytesGenerator(); g.init(params); val r = ByteArray(32); g.generateBytes(p, r, 0, 32); return r
    }
    private fun deriveSubKey(r: ByteArray, c: String): ByteArray {
        val h = HKDFBytesGenerator(SHA512Digest()); h.init(HKDFParameters(r, null, c.toByteArray(StandardCharsets.UTF_8))); val k = ByteArray(32); h.generateBytes(k, 0, 32); return k
    }
    private fun calculateHMAC(d: ByteArray, k: ByteArray): ByteArray {
        val h = HMac(SHA256Digest()); h.init(KeyParameter(k)); val o = ByteArray(h.macSize); h.update(d, 0, d.size); h.doFinal(o, 0); return o
    }
}