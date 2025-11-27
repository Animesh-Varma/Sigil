package dev.animeshvarma.sigil.model

import dev.animeshvarma.sigil.crypto.CryptoEngine

enum class SigilMode {
    AUTO, CUSTOM
}

enum class AppScreen(val title: String) {
    HOME("Home"),
    STEGANOGRAPHY("Steganography"),
    VEIL("Veil"),
    KEYSTORE("Keystore"),
    DONATE("Donate"),
    DOCS("Docs/Release Notes"),
    SETTINGS("Settings")
}

enum class CipherType { BLOCK, STREAM }
enum class CipherMode { GCM, CBC }

data class SigilAlgorithm(
    val id: String,
    val name: String,
    val description: String,
    val type: CipherType,
    val defaultMode: CipherMode
)

object AlgorithmRegistry {
    val supportedAlgorithms = listOf(
        SigilAlgorithm("AES_GCM", "AES-256", "Standard, Hardware Accelerated", CipherType.BLOCK, CipherMode.GCM),
        SigilAlgorithm("AES_CBC", "AES-256 (CBC)", "Standard Block Mode", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("TWOFISH_CBC", "Twofish", "Complex Key Schedule, Bruce Schneier", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("SERPENT_CBC", "Serpent", "High Security Margin (32 Rounds)", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("CAMELLIA_CBC", "Camellia", "EU Recommended, similar to AES", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("CAST6_CBC", "CAST-256", "RFC 2612, 128-bit Block", CipherType.BLOCK, CipherMode.CBC)
    )
}

data class UiState(
    // [FIX #3] Separate states for Auto and Custom
    val autoInput: String = "",
    val autoPassword: String = "",
    val autoOutput: String = "",

    val customInput: String = "",
    val customPassword: String = "",
    val customOutput: String = "",

    val selectedMode: SigilMode = SigilMode.AUTO,
    val currentScreen: AppScreen = AppScreen.HOME,
    val logs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val showLogsDialog: Boolean = false,

    val customLayers: List<CryptoEngine.Algorithm> = listOf(CryptoEngine.Algorithm.AES_GCM),
    val isCompressionEnabled: Boolean = true
)
