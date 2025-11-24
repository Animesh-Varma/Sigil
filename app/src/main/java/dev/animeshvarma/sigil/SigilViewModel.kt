package dev.animeshvarma.sigil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Enum for the different modes
enum class SigilMode {
    AUTO, CUSTOM, ADVANCED
}

// Data class for UI state
data class UiState(
    val inputText: String = "",
    val password: String = "",
    val outputText: String = "",
    val selectedMode: SigilMode = SigilMode.AUTO,
    val logs: List<String> = emptyList()
)

class SigilViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    
    fun onInputTextChanged(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }
    
    fun onPasswordChanged(newPassword: String) {
        _uiState.value = _uiState.value.copy(password = newPassword)
    }
    
    fun onOutputTextChanged(newText: String) {
        _uiState.value = _uiState.value.copy(outputText = newText)
    }
    
    fun onModeSelected(mode: SigilMode) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }
    
    fun addLog(message: String) {
        val updatedLogs = _uiState.value.logs + message
        _uiState.value = _uiState.value.copy(logs = updatedLogs)
    }
    
    fun onEncrypt() {
        // Add a log entry for the encryption attempt
        addLog("Encryption feature not implemented yet")

        // Update output text to indicate feature status
        _uiState.value = _uiState.value.copy(
            outputText = "Encryption: Feature not implemented yet"
        )
    }

    fun onDecrypt() {
        // Add a log entry for the decryption attempt
        addLog("Decryption feature not implemented yet")

        // Update output text to indicate feature status
        _uiState.value = _uiState.value.copy(
            outputText = "Decryption: Feature not implemented yet"
        )
    }
    
    fun onLogsClicked() {
        addLog("Logs accessed - Feature not implemented yet")
    }
    
    fun clearLogs() {
        _uiState.value = _uiState.value.copy(logs = emptyList())
    }
}