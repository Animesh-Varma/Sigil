package dev.animeshvarma.sigil.model

import dev.animeshvarma.sigil.crypto.CryptoEngine
import java.util.UUID

enum class SigilMode {
    AUTO, CUSTOM
}

enum class AppScreen(val title: String) {
    HOME("Home"),
    FILE_ENCRYPTION("File/Dir Encryption"),
    ASYMMETRIC("Asymmetric"),
    STEGANOGRAPHY("Steganography"),
    PARTITIONS("Partitions"),
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
        // The Heavyweights
        SigilAlgorithm("AES_GCM", "AES-256 (GCM)", "The global standard. Hardware accelerated, authenticated encryption. Fast and highly secure.", CipherType.BLOCK, CipherMode.GCM),
        SigilAlgorithm("AES_CBC", "AES-256 (CBC)", "Classic AES block mode. Good for compatibility, but GCM is generally preferred for integrity.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("TWOFISH_CBC", "Twofish", "AES finalist by Bruce Schneier. Complex key schedule makes it exceptionally resistant to brute-force attacks.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("SERPENT_CBC", "Serpent", "The 'Tank' of ciphers. AES runner-up with 32 rounds (vs AES's 14). Slower, but offers the highest theoretical security margin.", CipherType.BLOCK, CipherMode.CBC),

        // International Standards
        SigilAlgorithm("CAMELLIA_CBC", "Camellia", "EU (NESSIE) and Japan (CRYPTREC) recommended standard. Security and performance profile comparable to AES.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("SM4_CBC", "SM4", "Chinese National Wireless LAN standard (GB/T 32907). Used for data security in government systems.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("GOST_CBC", "GOST 28147", "Soviet/Russian government standard. Uses a simple Feistel network with 32 rounds. Known for its distinct structure.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("SEED_CBC", "SEED", "South Korean standard developed by KISA. Widely used in Asian banking and e-commerce security.", CipherType.BLOCK, CipherMode.CBC),

        // The Classics / AES Finalists
        SigilAlgorithm("CAST6_CBC", "CAST-256", "RFC 2612. An AES finalist derived from CAST-128. Known for its resistance to linear and differential cryptanalysis.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("RC6_CBC", "RC6", "Rivest (RSA) design. Simple and fast, relies on data-dependent rotations. AES finalist.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("BLOWFISH_CBC", "Blowfish", "Legacy Schneier design. 64-bit block size makes it vulnerable to birthday attacks on large files (>4GB), but fine for short text.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("IDEA_CBC", "IDEA", "The original PGP cipher. Once patented, now free. Uses 64-bit blocks and 128-bit keys.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("CAST5_CBC", "CAST-128", "The default cipher for GPG versions prior to 2.1. A solid 64-bit block cipher.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("TEA_CBC", "TEA", "Tiny Encryption Algorithm. Extremely simple code, but has weak keys. Used mostly for legacy or educational purposes.", CipherType.BLOCK, CipherMode.CBC),
        SigilAlgorithm("XTEA_CBC", "XTEA", "Extended TEA. Fixes weaknesses in TEA. Simple and efficient for small microcontrollers.", CipherType.BLOCK, CipherMode.CBC)
    )
}
data class LayerEntry(
    val id: String = UUID.randomUUID().toString(),
    val algorithm: CryptoEngine.Algorithm
)

data class UiState(
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

    val customLayers: List<LayerEntry> = listOf(
        LayerEntry(algorithm = CryptoEngine.Algorithm.AES_GCM)
    ),

    val isCompressionEnabled: Boolean = true
)