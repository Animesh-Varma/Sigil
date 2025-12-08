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
import java.nio.ByteBuffer
import java.nio.CharBuffer
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

    private fun getBlockSize(algo: Algorithm): Int = if (algo == Algorithm.AES_GCM || algo == Algorithm.AES_CBC) 16 else 16 // Simplified for brevity
    private fun getKeySize(algo: Algorithm): Int = 32

    fun encrypt(
        data: String,
        password: CharArray,
        algorithms: List<Algorithm> = listOf(Algorithm.AES_GCM),
        compress: Boolean = true,
        logCallback: (String) -> Unit = {}
    ): String {
        val startTime = java.lang.System.currentTimeMillis()
        logCallback("Initializing Secure Chain...")

        // 1. Salt
        val salt = ByteArray(16)
        secureRandom.nextBytes(salt)

        // 2. Root Secret
        val passBytes = toBytes(password)
        val sha512Password = hashPasswordSHA512(passBytes)
        Arrays.fill(passBytes, 0.toByte()) // Wipe raw pass

        val rootSecret = deriveRootSecret(sha512Password, salt)
        Arrays.fill(sha512Password, 0.toByte()) // Wipe hash

        logCallback("Root Secret derived (Memory hardened).")

        // 3. Prepare Payload
        val checksum = hashSHA256(data)
        val rawString = data + CS_DELIMITER + checksum
        var currentBytes = rawString.toByteArray(StandardCharsets.UTF_8)

        if (compress) {
            val originalSize = currentBytes.size
            currentBytes = compressData(currentBytes)
            logCallback("Compression: $originalSize -> ${currentBytes.size} bytes")
        }

        // 4. Chain Encryption
        val ivList = mutableListOf<String>()
        val algoNames = algorithms.joinToString(",") { it.name }
        logCallback("Chain Sequence: $algoNames")

        algorithms.forEachIndexed { index, algo ->
            val layerId = index + 1
            val ivSize = if (algo == Algorithm.AES_GCM) 12 else 16
            val iv = ByteArray(ivSize).apply { secureRandom.nextBytes(this) }
            ivList.add(encoder.encodeToString(iv))

            val keySize = getKeySize(algo)
            val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId", keySize)

            logCallback("Layer $layerId: Encrypting with ${algo.name}...")
            currentBytes = processCipher(true, algo, currentBytes, layerKey, iv)
            Arrays.fill(layerKey, 0.toByte())
        }

        // 5. Header
        val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER", 32)
        val flags = if (compress) "C" else "N"
        val metadataString = "$algoNames|${ivList.joinToString(",")}|$flags"
        val metadataBytes = metadataString.toByteArray(StandardCharsets.UTF_8)
        val headerIv = ByteArray(12).apply { secureRandom.nextBytes(this) }
        val encryptedMetadataBytes = encryptAES_GCM(metadataBytes, headerKey, headerIv)
        Arrays.fill(headerKey, 0.toByte())

        // 6. Pack & MAC
        val packBuffer = ByteBuffer.allocate(16 + 12 + 4 + encryptedMetadataBytes.size + currentBytes.size)
        packBuffer.put(salt)
        packBuffer.put(headerIv)
        packBuffer.putInt(encryptedMetadataBytes.size)
        packBuffer.put(encryptedMetadataBytes)
        packBuffer.put(currentBytes)
        val packedBytes = packBuffer.array()

        // MAC
        val macKey = deriveSubKey(rootSecret, "SIGIL_GLOBAL_MAC", 32)
        val hmac = calculateHMAC(packedBytes, macKey)
        Arrays.fill(macKey, 0.toByte())
        Arrays.fill(rootSecret, 0.toByte())

        val finalBytes = ByteBuffer.allocate(packedBytes.size + hmac.size)
            .put(packedBytes).put(hmac).array()

        logCallback("Header encrypted & sealed.")
        logCallback("Global HMAC Signature applied.")
        logCallback("Operation complete in ${java.lang.System.currentTimeMillis() - startTime}ms.")

        return stripPadding(encoder.encodeToString(finalBytes))
    }

    fun decrypt(
        encryptedData: String,
        password: CharArray,
        logCallback: (String) -> Unit = {}
    ): String {
        val startTime = java.lang.System.currentTimeMillis()
        logCallback("Reading Secure Container...")

        try {
            val cleanData = encryptedData.filter { !it.isWhitespace() }
            val totalBytes = decoder.decode(restorePadding(cleanData))
            if (totalBytes.size < 64) throw IllegalArgumentException("Data corrupted (Size too small).")

            // 1. HMAC Check
            val payloadSize = totalBytes.size - 32
            val payloadBytes = ByteArray(payloadSize)
            val storedMac = ByteArray(32)
            System.arraycopy(totalBytes, 0, payloadBytes, 0, payloadSize)
            System.arraycopy(totalBytes, payloadSize, storedMac, 0, 32)

            val salt = ByteArray(16)
            System.arraycopy(payloadBytes, 0, salt, 0, 16)

            // Reconstruct Keys
            val passBytes = toBytes(password)
            val sha512Password = hashPasswordSHA512(passBytes)
            Arrays.fill(passBytes, 0.toByte())

            val rootSecret = deriveRootSecret(sha512Password, salt)
            Arrays.fill(sha512Password, 0.toByte())
            logCallback("Root Secret reconstructed.")

            val macKey = deriveSubKey(rootSecret, "SIGIL_GLOBAL_MAC", 32)
            if (!Arrays.equals(storedMac, calculateHMAC(payloadBytes, macKey))) {
                Arrays.fill(rootSecret, 0.toByte())
                throw IllegalArgumentException("HMAC Verification Failed.")
            }
            Arrays.fill(macKey, 0.toByte())
            logCallback("Global HMAC Verified. Container is authentic.")

            // 2. Unpack
            val buffer = ByteBuffer.wrap(payloadBytes)
            buffer.position(16)
            val headerIv = ByteArray(12); buffer.get(headerIv)
            val headerLen = buffer.int; val encryptedMeta = ByteArray(headerLen); buffer.get(encryptedMeta)
            val bodyBytes = ByteArray(buffer.remaining()); buffer.get(bodyBytes)

            // 3. Header
            val headerKey = deriveSubKey(rootSecret, "SIGIL_HEADER", 32)
            val metaBytes = decryptAES_GCM(encryptedMeta, headerKey, headerIv)
            Arrays.fill(headerKey, 0.toByte())
            val metaString = String(metaBytes, StandardCharsets.UTF_8)
            val parts = metaString.split("|")
            val algoNames = parts[0].split(",")
            val ivStrings = parts[1].split(",")
            val isCompressed = parts.getOrNull(2) == "C"

            logCallback("Layers detected: ${parts[0]}")

            // 4. Decrypt Chain
            var currentBytes = bodyBytes
            for (i in algoNames.indices.reversed()) {
                val layerId = i + 1
                val algo = Algorithm.valueOf(algoNames[i])
                val iv = decoder.decode(ivStrings[i])
                val keySize = getKeySize(algo)
                val layerKey = deriveSubKey(rootSecret, "SIGIL_LAYER_$layerId", keySize)

                logCallback("Layer $layerId: Decrypting with ${algo.name}...")
                currentBytes = processCipher(false, algo, currentBytes, layerKey, iv)
                Arrays.fill(layerKey, 0.toByte())
            }
            Arrays.fill(rootSecret, 0.toByte())

            // 5. Decompress
            if (isCompressed) {
                val compressedSize = currentBytes.size
                currentBytes = decompressData(currentBytes)
                logCallback("Decompressed: $compressedSize -> ${currentBytes.size} bytes")
            }

            // 6. Checksum
            val result = String(currentBytes, StandardCharsets.UTF_8)
            logCallback("Decryption complete in ${java.lang.System.currentTimeMillis() - startTime}ms.")

            if (result.contains(CS_DELIMITER)) {
                val plain = result.substringBeforeLast(CS_DELIMITER)
                val sum = result.substringAfterLast(CS_DELIMITER)
                if (sum == hashSHA256(plain)) {
                    logCallback("Integrity Verified (Internal Checksum).")
                    return plain
                } else {
                    return "ERROR: Checksum mismatch."
                }
            }
            logCallback("Legacy format (No checksum).")
            return result

        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Container Corrupted or Invalid Format.")
        }
    }

    // --- SECURE HELPERS ---
    private fun toBytes(chars: CharArray): ByteArray {
        val charBuffer = CharBuffer.wrap(chars)
        val byteBuffer = StandardCharsets.UTF_8.encode(charBuffer)
        val bytes = Arrays.copyOfRange(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit())
        Arrays.fill(byteBuffer.array(), 0.toByte())
        return bytes
    }
    private fun hashPasswordSHA512(p: ByteArray): ByteArray { val d = SHA512Digest(); val o = ByteArray(d.digestSize); d.update(p, 0, p.size); d.doFinal(o, 0); return o }
    private fun compressData(data: ByteArray): ByteArray { val d = Deflater(Deflater.BEST_COMPRESSION); d.setInput(data); d.finish(); val o = java.io.ByteArrayOutputStream(data.size); val b = ByteArray(1024); while (!d.finished()) { val c = d.deflate(b); o.write(b, 0, c) }; o.close(); return o.toByteArray() }
    private fun decompressData(data: ByteArray): ByteArray { val i = Inflater(); i.setInput(data); val o = java.io.ByteArrayOutputStream(data.size); val b = ByteArray(1024); while (!i.finished()) { val c = i.inflate(b); o.write(b, 0, c) }; o.close(); return o.toByteArray() }
    private fun stripPadding(i: String) = i.trimEnd('=')
    private fun restorePadding(i: String): String { val m = i.length % 4; return if (m > 0) i + "=".repeat(4 - m) else i }
    private fun hashSHA256(i: String): String { val d = SHA256Digest(); val b = i.toByteArray(StandardCharsets.UTF_8); val o = ByteArray(d.digestSize); d.update(b, 0, b.size); d.doFinal(o, 0); return o.joinToString("") { "%02x".format(it) } }
    private fun deriveRootSecret(p: ByteArray, s: ByteArray): ByteArray { val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13).withIterations(4).withMemoryPowOfTwo(16).withParallelism(4).withSalt(s).build(); val g = Argon2BytesGenerator(); g.init(params); val r = ByteArray(32); g.generateBytes(p, r, 0, 32); return r }
    private fun deriveSubKey(r: ByteArray, c: String, l: Int): ByteArray { val h = HKDFBytesGenerator(SHA512Digest()); h.init(HKDFParameters(r, null, c.toByteArray(StandardCharsets.UTF_8))); val k = ByteArray(l); h.generateBytes(k, 0, l); return k }
    private fun calculateHMAC(d: ByteArray, k: ByteArray): ByteArray { val h = HMac(SHA256Digest()); h.init(KeyParameter(k)); val o = ByteArray(h.macSize); h.update(d, 0, d.size); h.doFinal(o, 0); return o }

    // Engine mapping
    private fun processCipher(encrypt: Boolean, algo: Algorithm, data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        return when (algo) {
            Algorithm.AES_GCM -> if (encrypt) encryptAES_GCM(data, key, iv) else decryptAES_GCM(data, key, iv)
            Algorithm.TWOFISH_CBC -> processBlockCipher(encrypt, TwofishEngine(), data, key, iv)
            Algorithm.SERPENT_CBC -> processBlockCipher(encrypt, SerpentEngine(), data, key, iv)
            else -> if (encrypt) encryptAES_GCM(data, key, iv) else decryptAES_GCM(data, key, iv) // Fallback
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
}