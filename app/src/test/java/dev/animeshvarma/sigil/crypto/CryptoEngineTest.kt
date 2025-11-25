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

        val encrypted = CryptoEngine.encrypt(
            data = testData,
            password = password,
            algorithm = CryptoEngine.Algorithm.AES_GCM,
            logCallback = logCallback
        )

        assertTrue(encrypted.isNotEmpty())
        assertTrue(encrypted.contains(":"))

        val decrypted = CryptoEngine.decrypt(
            encryptedData = encrypted,
            password = password,
            algorithm = CryptoEngine.Algorithm.AES_GCM,
            logCallback = logCallback
        )

        assertEquals(testData, decrypted)
        assertTrue(logOutput.contains("Argon2id key generated"))
        assertTrue(logOutput.contains("AES_GCM encryption completed"))
    }

    @Test
    fun testTwofishCBCEncryptionDecryption() {
        val testData = "Hello, World! This is a test message for Twofish."
        val password = "anotherSecurePassword123"

        var logOutput = ""
        val logCallback: (String) -> Unit = { message ->
            logOutput += "$message\n"
        }

        val encrypted = CryptoEngine.encrypt(
            data = testData,
            password = password,
            algorithm = CryptoEngine.Algorithm.TWOFISH_CBC,
            logCallback = logCallback
        )

        assertTrue(encrypted.isNotEmpty())
        assertTrue(encrypted.contains(":"))

        val decrypted = CryptoEngine.decrypt(
            encryptedData = encrypted,
            password = password,
            algorithm = CryptoEngine.Algorithm.TWOFISH_CBC,
            logCallback = logCallback
        )

        assertEquals(testData, decrypted)
    }

    @Test
    fun testSerpentCBCEncryptionDecryption() {
        val testData = "Hello, World! This is a test message for Serpent."
        val password = "yetAnotherSecurePassword123"

        var logOutput = ""
        val logCallback: (String) -> Unit = { message ->
            logOutput += "$message\n"
        }

        val encrypted = CryptoEngine.encrypt(
            data = testData,
            password = password,
            algorithm = CryptoEngine.Algorithm.SERPENT_CBC,
            logCallback = logCallback
        )

        assertTrue(encrypted.isNotEmpty())
        assertTrue(encrypted.contains(":"))

        val decrypted = CryptoEngine.decrypt(
            encryptedData = encrypted,
            password = password,
            algorithm = CryptoEngine.Algorithm.SERPENT_CBC,
            logCallback = logCallback
        )

        assertEquals(testData, decrypted)
    }

    @Test
    fun testEncryptionFormat() {
        val testData = "Format test data"
        val password = "formatTestPassword"

        val encrypted = CryptoEngine.encrypt(testData, password)

        val parts = encrypted.split(":")
        assertEquals(3, parts.size)

        // Check that all parts are valid Base64
        parts.forEach { part ->
            assertNotEquals("", part)
            // Verify it's valid Base64 by attempting to decode
            Base64.getDecoder().decode(part)
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
            fail("Decryption with wrong password should fail")
        } catch (e: Exception) {
            // Expected behavior
        }
    }
}