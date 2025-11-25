package dev.animeshvarma.sigil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.animeshvarma.sigil.crypto.CryptoEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Modes of operation for Sigil.
 * AUTO: Standard AES-GCM (Military Grade).
 * CUSTOM: User selects specific algorithms (e.g., Twofish).
 * ADVANCED: Layered encryption chains (Future Feature).
 */
enum class SigilMode {
    AUTO, CUSTOM, ADVANCED
}

/**
 * Holds the entire state of the main screen.
 */
data class UiState(
    val inputText: String = "",
    val password: String = "",
    val outputText: String = "",
    val selectedMode: SigilMode = SigilMode.AUTO,
    val logs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val showLogsDialog: Boolean = false
)

class SigilViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    // --- User Input Handlers ---

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(password = newPassword) }
    }

    fun onModeSelected(mode: SigilMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun onLogsClicked() {
        // Toggle logs dialog visibility or add a marker log
        _uiState.update { it.copy(showLogsDialog = !it.showLogsDialog) }
        addLog("Logs accessed by user")
    }

    // --- Crypto Logic ---
    fun onEncrypt() {
        val currentState = _uiState.value
        if (currentState.password.isEmpty()) {
            addLog("❌ Error: Password required")
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // DEFINE THE CHAINS HERE
                val chain = when (currentState.selectedMode) {
                    SigilMode.AUTO -> listOf(
                        CryptoEngine.Algorithm.AES_GCM
                    )
                    SigilMode.CUSTOM -> listOf(
                        CryptoEngine.Algorithm.TWOFISH_CBC
                    )
                    SigilMode.ADVANCED -> listOf(
                        CryptoEngine.Algorithm.TWOFISH_CBC,
                        CryptoEngine.Algorithm.AES_GCM
                    ) // Real Multi-Layer Chaining!
                }

                addLog("🔒 Encrypting with chain: ${chain.joinToString(" -> ")}")

                val encrypted = CryptoEngine.encrypt(
                    data = currentState.inputText,
                    password = currentState.password,
                    algorithms = chain, // Pass the list
                    logCallback = { addLog(it) }
                )

                _uiState.update { it.copy(outputText = encrypted, isLoading = false) }
                addLog("✅ Encryption Complete.")

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, outputText = "Error") }
                addLog("❌ Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun onDecrypt() {
        val currentState = _uiState.value
        if (currentState.password.isEmpty()) {
            addLog("❌ Error: Password required")
            return
        }
        if (currentState.inputText.isEmpty()) {
            addLog("⚠️ No text to decrypt")
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                addLog("🔓 analyzing header and decrypting...")

                // NOTE: We don't pass an algorithm anymore.
                // The decrypt function reads the header to find the chain automatically.
                val decrypted = CryptoEngine.decrypt(
                    encryptedData = currentState.inputText,
                    password = currentState.password,
                    logCallback = { addLog(it) }
                )

                _uiState.update { it.copy(outputText = decrypted, isLoading = false) }
                addLog("✅ Decryption Successful.")

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, outputText = "Decryption Failed") }
                addLog("❌ Error: ${e.message}")
            }
        }
    }


    // --- Utilities ---

    /**
     * Helper to safely update logs in an atomic way.
     * Thread-safe for calls from Background/IO threads.
     */
    fun addLog(message: String) {
        _uiState.update {
            // Keep only the last 50 logs to save memory
            val newLogs = (it.logs + message).takeLast(50)
            it.copy(logs = newLogs)
        }
    }

    /**
     * Security Feature: Wipes sensitive data from state.
     * Call this when the app is backgrounded or closed.
     */
    fun clearSensitiveData() {
        _uiState.update {
            it.copy(
                password = "",
                inputText = "",
                outputText = "",
                logs = emptyList()
            )
        }
    }
}