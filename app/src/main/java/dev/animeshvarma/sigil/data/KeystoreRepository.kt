package dev.animeshvarma.sigil.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.util.SecureMemory
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Data Model
data class VaultEntry(
    val alias: String,
    val timestamp: Long,
    val strengthScore: Int,
    val strengthLabel: String
)

class KeystoreRepository(context: Context) {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val prefs: SharedPreferences = context.getSharedPreferences("sigil_vault_v3", Context.MODE_PRIVATE)

    private val HARDWARE_ALIAS = "SIGIL_HARDWARE_WRAPPER"
    private val VAULT_KEY_PREF = "ENCRYPTED_VAULT_SEED"

    // The "Trio" Chain
    private val STORAGE_ALGORITHMS = listOf(
        CryptoEngine.Algorithm.AES_GCM,
        CryptoEngine.Algorithm.TWOFISH_CBC,
        CryptoEngine.Algorithm.SERPENT_CBC
    )

    init {
        ensureVaultInitialized()
    }

    /**
     * Ensures a random 256-bit "Vault Key" exists, encrypted by the Hardware Keystore.
     */
    private fun ensureVaultInitialized() {
        if (!prefs.contains(VAULT_KEY_PREF)) {
            // 1. Generate Hardware Wrapper Key
            if (!keyStore.containsAlias(HARDWARE_ALIAS)) {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(HARDWARE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                generator.init(spec)
                generator.generateKey()
            }

            // 2. Generate Random Vault Seed (The actual key for Trio)
            val vaultSeed = ByteArray(32) // 256 bits
            SecureRandom().nextBytes(vaultSeed)

            // 3. Encrypt Seed with Hardware Key
            val hwKey = keyStore.getKey(HARDWARE_ALIAS, null) as SecretKey
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, hwKey)
            val iv = cipher.iv
            val encryptedSeed = cipher.doFinal(vaultSeed)

            // 4. Pack & Save
            val blob = ByteArray(1 + iv.size + encryptedSeed.size)
            blob[0] = iv.size.toByte()
            System.arraycopy(iv, 0, blob, 1, iv.size)
            System.arraycopy(encryptedSeed, 0, blob, 1 + iv.size, encryptedSeed.size)

            prefs.edit().putString(VAULT_KEY_PREF, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()

            // 5. Wipe memory
            Arrays.fill(vaultSeed, 0.toByte())
        }
    }

    /**
     * Unwraps the Hardware-Encrypted Seed to get the usable Vault Key (CharArray).
     * CALLER MUST WIPE RESULT.
     */
    private fun getVaultKey(): CharArray {
        val base64Blob = prefs.getString(VAULT_KEY_PREF, null) ?: throw IllegalStateException("Vault corrupted")
        val blob = Base64.decode(base64Blob, Base64.NO_WRAP)

        val ivSize = blob[0].toInt()
        val iv = ByteArray(ivSize)
        val encryptedSeed = ByteArray(blob.size - 1 - ivSize)
        System.arraycopy(blob, 1, iv, 0, ivSize)
        System.arraycopy(blob, 1 + ivSize, encryptedSeed, 0, encryptedSeed.size)

        val hwKey = keyStore.getKey(HARDWARE_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, hwKey, GCMParameterSpec(128, iv))

        val seedBytes = cipher.doFinal(encryptedSeed)

        // Convert to Hex CharArray to use as "Password" for CryptoEngine
        // This effectively turns the random bytes into a text password for the engine
        val hexChars = toHex(seedBytes)

        Arrays.fill(seedBytes, 0.toByte())
        return hexChars
    }

    fun saveToVault(alias: String, secret: String, score: Int, label: String) {
        val vaultKey = getVaultKey() // Valid Trio Key

        try {
            // Encrypt using the Full Trio Chain
            val encryptedBlob = CryptoEngine.encrypt(
                data = secret,
                password = vaultKey,
                algorithms = STORAGE_ALGORITHMS,
                compress = false // Small strings, compression negligible
            )

            prefs.edit()
                .putString("DATA_$alias", encryptedBlob)
                .putLong("TIME_$alias", System.currentTimeMillis())
                .putInt("SCORE_$alias", score)
                .putString("LABEL_$alias", label)
                .apply()

        } finally {
            SecureMemory.wipe(vaultKey)
        }
    }

    fun loadFromVault(alias: String): String? {
        val encryptedBlob = prefs.getString("DATA_$alias", null) ?: return null
        val vaultKey = getVaultKey()

        return try {
            CryptoEngine.decrypt(
                encryptedData = encryptedBlob,
                password = vaultKey
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            SecureMemory.wipe(vaultKey)
        }
    }

    fun getEntries(): List<VaultEntry> {
        return prefs.all.keys.filter { it.startsWith("DATA_") }.map { key ->
            val alias = key.removePrefix("DATA_")
            VaultEntry(
                alias = alias,
                timestamp = prefs.getLong("TIME_$alias", 0L),
                strengthScore = prefs.getInt("SCORE_$alias", 0),
                strengthLabel = prefs.getString("LABEL_$alias", "") ?: ""
            )
        }.sortedByDescending { it.timestamp }
    }

    fun deleteEntry(alias: String) {
        prefs.edit().remove("DATA_$alias").remove("TIME_$alias").remove("SCORE_$alias").remove("LABEL_$alias").apply()
    }

    private fun toHex(bytes: ByteArray): CharArray {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return hexChars
    }

    companion object {
        private val hexArray = "0123456789ABCDEF".toCharArray()
    }
}