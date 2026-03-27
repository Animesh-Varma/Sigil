package dev.animeshvarma.sigil.model

import dev.animeshvarma.sigil.crypto.CryptoEngine
import java.util.UUID

enum class SigilMode {
    AUTO, CUSTOM
}

enum class LockMode {
    NONE,
    DEVICE,
    CUSTOM
}

enum class AppScreen(val title: String) {
    HOME("Home"),
    HEADERLESS("Headerless Mode"),
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
enum class CipherMode { GCM, CBC, POLY1305 }

enum class LockType { PIN, PASSWORD }

data class SigilAlgorithm(
    val id: String,
    val name: String,
    val description: String,
    val type: CipherType,
    val defaultMode: CipherMode,
    val isWeak: Boolean = false,
    val securityWarning: String? = null
)

data class EncryptionProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val layers: List<CryptoEngine.Algorithm>,
    val kdfConfig: CryptoEngine.KdfConfig? = null,
    val isBuiltIn: Boolean = false,
    val isCompressionEnabled: Boolean = true,
    val isRaw: Boolean = false
)

object ProfileRegistry {
    const val STANDARD_AES_ID = "sigil_standard_aes"

    val defaultProfile = EncryptionProfile(
        id = "sigil_default_chain",
        name = "Sigil Chain",
        description = "Sigil's hybrid stack. XChaCha20 + Serpent + Twofish + AES (All AEAD).",
        layers = listOf(
            CryptoEngine.Algorithm.XCHACHA20_POLY1305,
            CryptoEngine.Algorithm.SERPENT_GCM,
            CryptoEngine.Algorithm.TWOFISH_GCM,
            CryptoEngine.Algorithm.AES_GCM
        ),
        isBuiltIn = true,
        isCompressionEnabled = true,
        isRaw = false
    )

    val standardProfile = EncryptionProfile(
        id = STANDARD_AES_ID,
        name = "Standard AES",
        description = "Standalone AES-256-GCM. No chaining, no headers, no metadata. For other raw algorithms, use Custom tab. Auto-decrypt unsupported; requires manual profile selection.",
        layers = listOf(CryptoEngine.Algorithm.AES_GCM),
        isBuiltIn = true,
        isCompressionEnabled = false,
        isRaw = true
    )

    val builtInProfiles = listOf(defaultProfile, standardProfile)
}

object AlgorithmRegistry {
    // Helper to prevent structural duplication
    private fun algo(
        id: String,
        name: String,
        desc: String,
        type: CipherType = CipherType.BLOCK,
        mode: CipherMode = CipherMode.GCM,
        isWeak: Boolean = false,
        warning: String? = null
    ) = SigilAlgorithm(id, name, desc, type, mode, isWeak, warning)

    val supportedAlgorithms = listOf(
        // --- AEAD / GCM BLOCK CIPHERS ---
        algo("AES_GCM", "AES-256 (GCM)", "The global standard. Hardware accelerated, authenticated encryption (AEAD). Fast and highly secure."),
        algo("ARIA_256_GCM", "ARIA-256 (GCM)", "South Korean standard (RFC 5794). 128-bit block, 256-bit key. High-security NIST alternative."),
        algo("CAMELLIA_GCM", "Camellia (GCM)", "EU/Japan standard with Authenticated Encryption. Security profile comparable to AES."),
        algo("SERPENT_GCM", "Serpent (GCM)", "The 'Tank' upgraded with GCM. Offers the highest theoretical security margin with built-in integrity."),
        algo("TWOFISH_GCM", "Twofish (GCM)", "Bruce Schneier's robust cipher, wrapped in GCM for authenticated encryption."),
        algo("SM4_GCM", "SM4 (GCM)", "Chinese National standard upgraded with GCM. Mandated for government data security in China."),

        // --- STREAM CIPHERS (AEAD) ---
        algo("XCHACHA20_POLY1305", "XChaCha20-Poly1305", "Extended-nonce variant (192-bit). Eliminates random nonce collision risks.", CipherType.STREAM, CipherMode.POLY1305),
        algo("CHACHA20_POLY1305", "ChaCha20-Poly1305", "High-speed stream cipher by D. J. Bernstein. Immune to padding oracle and timing attacks.", CipherType.STREAM, CipherMode.POLY1305),

        // --- LEGACY CBC BLOCK CIPHERS (128-bit) ---
        algo("AES_CBC", "AES-256 (CBC)", "Classic AES. Good compatibility, but GCM is preferred for built-in integrity checks.", mode = CipherMode.CBC),
        algo("CAMELLIA_CBC", "Camellia (CBC)", "EU (NESSIE) and Japan (CRYPTREC) standard in classic CBC mode.", mode = CipherMode.CBC),
        algo("SERPENT_CBC", "Serpent (CBC)", "AES runner-up with 32 rounds. Classic CBC implementation.", mode = CipherMode.CBC),
        algo("TWOFISH_CBC", "Twofish (CBC)", "Complex key schedule makes it exceptionally resistant to brute-force attacks. CBC mode.", mode = CipherMode.CBC),
        algo("SM4_CBC", "SM4 (CBC)", "Chinese National Wireless LAN standard (GB/T 32907). CBC mode.", mode = CipherMode.CBC),
        algo("CAST6_CBC", "CAST-256", "RFC 2612. An AES finalist known for resistance to linear and differential cryptanalysis.", mode = CipherMode.CBC),
        algo("RC6_CBC", "RC6", "Rivest (RSA) design. Simple and fast, relies on data-dependent rotations.", mode = CipherMode.CBC),
        algo("SEED_CBC", "SEED", "South Korean standard (KISA). Widely used in Asian banking security.", mode = CipherMode.CBC),

        // --- WEAK / LEGACY CIPHERS (64-bit) ---
        algo("BLOWFISH_CBC", "Blowfish", "Legacy Schneier design. Fast for short text.", mode = CipherMode.CBC, isWeak = true, warning = "64-bit Block Size. Vulnerable to birthday attacks on large files."),
        algo("IDEA_CBC", "IDEA", "The original PGP cipher. Uses 128-bit keys.", mode = CipherMode.CBC, isWeak = true, warning = "64-bit Block Size. Legacy algorithm."),
        algo("CAST5_CBC", "CAST-128", "Default cipher for older GPG versions.", mode = CipherMode.CBC, isWeak = true, warning = "64-bit Block Size. Legacy algorithm."),
        algo("GOST_CBC", "GOST 28147", "Soviet/Russian standard. 32-round Feistel network.", mode = CipherMode.CBC, isWeak = true, warning = "64-bit Block Size. Theoretically vulnerable to advanced analysis."),
        algo("TEA_CBC", "TEA", "Tiny Encryption Algorithm. Extremely simple code.", mode = CipherMode.CBC, isWeak = true, warning = "Weak Key Schedule. Vulnerable to equivalent key attacks."),
        algo("XTEA_CBC", "XTEA", "Extended TEA. Fixes some TEA weaknesses.", mode = CipherMode.CBC, isWeak = true, warning = "64-bit Block Size. Educational/Legacy use only.")
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
    val availableProfiles: List<EncryptionProfile> = ProfileRegistry.builtInProfiles,
    val activeProfile: EncryptionProfile = ProfileRegistry.defaultProfile,
    val editingProfileId: String? = null,
    val isDemoDropdownExpanded: Boolean = false,
    val isDemoDrawerOpen: Boolean = false,
    val customLayers: List<LayerEntry> = listOf(LayerEntry(algorithm = CryptoEngine.Algorithm.AES_GCM)),
    val isCompressionEnabled: Boolean = true
)