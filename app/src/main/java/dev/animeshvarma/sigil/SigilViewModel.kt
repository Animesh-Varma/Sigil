package dev.animeshvarma.sigil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.SigilMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UiState(
    val inputText: String = "",
    val password: String = "",
    val outputText: String = "",
    val selectedMode: SigilMode = SigilMode.AUTO,
    val currentScreen: AppScreen = AppScreen.HOME, // [ADDED]
    val logs: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val showLogsDialog: Boolean = false
)

class SigilViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun onInputTextChanged(newText: String) {
        _uiState.update { it.copy(inputText = newText) }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update { it.copy(password = newPassword) }
    }

    fun onModeSelected(mode: SigilMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    // [ADDED] Handler for Side Menu navigation
    fun onScreenSelected(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun onLogsClicked() {
        _uiState.update { it.copy(showLogsDialog = !it.showLogsDialog) }
    }

    fun onEncrypt() {
        val currentState = _uiState.value

        if (currentState.password.isEmpty()) {
            addLog("Error: Encryption aborted. Password is required.")
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        addLog("Encryption process started.")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Determine Chain
                val chain = when (currentState.selectedMode) {
                    SigilMode.AUTO -> {
                        val randomChain = listOf(
                            CryptoEngine.Algorithm.AES_GCM,
                            CryptoEngine.Algorithm.TWOFISH_CBC,
                            CryptoEngine.Algorithm.SERPENT_CBC
                        ).shuffled()
                        addLog("Auto Mode: Randomized layer sequence.")
                        randomChain
                    }
                    SigilMode.CUSTOM -> listOf(CryptoEngine.Algorithm.TWOFISH_CBC) // Placeholder
                }

                val encrypted = CryptoEngine.encrypt(
                    data = currentState.inputText,
                    password = currentState.password,
                    algorithms = chain,
                    logCallback = { addLog(it) }
                )

                _uiState.update { it.copy(outputText = encrypted, isLoading = false) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, outputText = "Error") }
                addLog("Critical Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun onDecrypt() {
        val currentState = _uiState.value
        if (currentState.password.isEmpty()) {
            addLog("Error: Password required for decryption.")
            return
        }
        if (currentState.inputText.isEmpty()) {
            addLog("Warning: No input text found.")
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        addLog("Decryption process started.")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decrypted = CryptoEngine.decrypt(
                    encryptedData = currentState.inputText,
                    password = currentState.password,
                    logCallback = { addLog(it) }
                )

                _uiState.update { it.copy(outputText = decrypted, isLoading = false) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, outputText = "Decryption Failed") }
                addLog("Error: ${e.message}")
            }
        }
    }

    fun addLog(message: String) {
        val timestamp = timeFormat.format(Date())
        val formattedLog = "[$timestamp] $message"
        _uiState.update {
            val newLogs = (it.logs + formattedLog).takeLast(100)
            it.copy(logs = newLogs)
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(logs = emptyList()) }
    }

    fun clearSensitiveData() {
        _uiState.update { it.copy(password = "", inputText = "", outputText = "", logs = emptyList()) }
    }
}