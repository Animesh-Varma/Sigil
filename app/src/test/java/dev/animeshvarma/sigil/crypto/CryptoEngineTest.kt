package dev.animeshvarma.sigil.crypto

import org.junit.Test
import org.junit.Assert.*
import java.util.Base64

class CryptoEngineTest {

    @Test
    fun testArgon2KeyDerivation() {
        val password = "testPassword123"
        val salt = "testSalt12345678".toByteArray() // 16 bytes
        val key = CryptoEngine.deriveKey(password.toCharArray(), salt, 32)

        assertEquals(32, key.size)
        assertTrue(key.isNotEmpty())
    }

    @Test
    fun testAESGCMEncryptionDecryption() {
        val testData = "Hello, World! This is a test message."
        val password = "mySecurePassword123"

        var logOutput = ""
        val logCallback: (String) -> Unit = { message ->
            logOutput += "$message\n"
        }

        // FIXED: Pass a List of algorithms
        val encrypted = CryptoEngine.encrypt(
            data = testData,
            password = password,
            algorithms = listOf(CryptoEngine.Algorithm.AES_GCM),
            logCallback = logCallback
        )

        assertTrue(encrypted.isNotEmpty())
        assertTrue(encrypted.count { it == ':' } == 3) // Expect 3 delimiters (4 parts)

        // FIXED: Decrypt no longer needs the Algorithm arg (it reads the header)
        val decrypted = CryptoEngine.decrypt(
            encryptedData = encrypted,
            password = password,
            logCallback = logCallback
        )

        assertEquals(testData, decrypted)
        assertTrue(logOutput.contains("Argon2id Master Key derived"))
    }

    @Test
    fun testTwofishCBCEncryptionDecryption() {
        val testData = "Hello, World! This is a test message for Twofish."
        val password = "anotherSecurePassword123"

        // FIXED: Pass List
        val encrypted = CryptoEngine.encrypt(
            data = testData,
            password = password,
            algorithms = listOf(CryptoEngine.Algorithm.TWOFISH_CBC)
        )

        val decrypted = CryptoEngine.decrypt(
            encryptedData = encrypted,
            password = password
        )

        assertEquals(testData, decrypted)
    }

    @Test
    fun testSerpentCBCEncryptionDecryption() {
        val testData = "Hello, World! This is a test message for Serpent."
        val password = "yetAnotherSecurePassword123"

        val encrypted = CryptoEngine.encrypt(
            data = testData,
            password = password,
            algorithms = listOf(CryptoEngine.Algorithm.SERPENT_CBC)
        )

        val decrypted = CryptoEngine.decrypt(
            encryptedData = encrypted,
            password = password
        )

        assertEquals(testData, decrypted)
    }

    @Test
    fun testMultiLayerChaining() {
        // NEW TEST: Verifies that "Advanced Mode" chaining works
        val testData = "Top Secret Multi-Layer Data"
        val password = "ChainPassword"

        // Chain: Twofish -> AES
        val chain = listOf(
            CryptoEngine.Algorithm.TWOFISH_CBC,
            CryptoEngine.Algorithm.AES_GCM
        )

        val encrypted = CryptoEngine.encrypt(
            data = testData,
            password = password,
            algorithms = chain
        )

        // Verify header contains both algorithms
        val parts = encrypted.split(":")
        val algoHeader = parts[1] // The second part is ALGO_IDs
        assertTrue(algoHeader.contains("TWOFISH_CBC"))
        assertTrue(algoHeader.contains("AES_GCM"))

        // Decrypt (Auto-detects chain)
        val decrypted = CryptoEngine.decrypt(
            encryptedData = encrypted,
            password = password
        )

        assertEquals(testData, decrypted)
    }

    @Test
    fun testEncryptionFormat() {
        val testData = "Format test data"
        val password = "formatTestPassword"

        val encrypted = CryptoEngine.encrypt(testData, password) // Uses default AES

        val parts = encrypted.split(":")
        // FIXED: New format is SALT:ALGO:IV:CIPHER (4 parts)
        assertEquals(4, parts.size)

        // Check that Salt, IVs, and Cipher are valid Base64
        // Parts[1] is the Algo list (plain text), so skip checking Base64 on it
        listOf(0, 2, 3).forEach { index ->
            assertNotEquals("", parts[index])
            // Verify it's valid Base64
            // Note: parts[2] (IVs) might be comma-separated if chained,
            // but for default single algo, it's just one Base64 string.
            val ivs = parts[index].split(",")
            ivs.forEach { iv ->
                Base64.getDecoder().decode(iv)
            }
        }
    }

    @Test
    fun testWrongPasswordDecryption() {
        val testData = "Secret message"
        val password = "correctPassword"
        val wrongPassword = "wrongPassword"

        val encrypted = CryptoEngine.encrypt(testData, password)

        try {
            CryptoEngine.decrypt(encrypted, wrongPassword)
            fail("Decryption with wrong password should fail (Argon2 derived key won't match)")
        } catch (e: Exception) {
            // Expected behavior: Likely a padding exception or mac check failure
        }
    }
}