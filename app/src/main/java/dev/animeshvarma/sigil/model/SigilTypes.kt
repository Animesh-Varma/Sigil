package dev.animeshvarma.sigil.model

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