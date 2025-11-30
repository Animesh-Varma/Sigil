package dev.animeshvarma.sigil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.model.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SigilViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // [FIX #3] Logic to update the correct field based on active mode
    fun onInputTextChanged(newText: String) {
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoInput = newText)
            else it.copy(customInput = newText)
        }
    }

    fun moveLayer(fromIndex: Int, toIndex: Int) {
        val list = _uiState.value.customLayers.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _uiState.update { it.copy(customLayers = list) }
        }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoPassword = newPassword)
            else it.copy(customPassword = newPassword)
        }
    }

    fun onModeSelected(mode: SigilMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun onScreenSelected(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun onLogsClicked() {
        _uiState.update { it.copy(showLogsDialog = !it.showLogsDialog) }
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
        _uiState.update {
            it.copy(
                autoPassword = "", autoInput = "", autoOutput = "",
                customPassword = "", customInput = "", customOutput = "",
                logs = emptyList()
            )
        }
    }
    fun addLayers(algos: List<CryptoEngine.Algorithm>) {
        _uiState.update { it.copy(customLayers = it.customLayers + algos) }
    }

    fun removeLayer(index: Int) {
        val mutable = _uiState.value.customLayers.toMutableList()
        if (index in mutable.indices) {
            mutable.removeAt(index)
            _uiState.update { it.copy(customLayers = mutable) }
        }
    }

    fun toggleCompression(enabled: Boolean) {
        _uiState.update { it.copy(isCompressionEnabled = enabled) }
    }

    fun onEncrypt() {
        val state = _uiState.value
        // Select correct inputs
        val pwd = if (state.selectedMode == SigilMode.AUTO) state.autoPassword else state.customPassword
        val input = if (state.selectedMode == SigilMode.AUTO) state.autoInput else state.customInput

        if (pwd.isEmpty()) {
            addLog("Error: Encryption aborted. Password is required.")
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        addLog("Encryption process started.")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (chain, compress) = if (state.selectedMode == SigilMode.AUTO) {
                    val randomChain = listOf(
                        CryptoEngine.Algorithm.AES_GCM,
                        CryptoEngine.Algorithm.TWOFISH_CBC,
                        CryptoEngine.Algorithm.SERPENT_CBC
                    ).shuffled()
                    addLog("Auto Mode: Randomized layer sequence.")
                    Pair(randomChain, true)
                } else {
                    Pair(state.customLayers, state.isCompressionEnabled)
                }

                if (chain.isEmpty()) throw Exception("No encryption layers selected.")

                val result = CryptoEngine.encrypt(
                    data = input,
                    password = pwd,
                    algorithms = chain,
                    compress = compress,
                    logCallback = { addLog(it) }
                )

                _uiState.update {
                    if (it.selectedMode == SigilMode.AUTO) it.copy(autoOutput = result, isLoading = false)
                    else it.copy(customOutput = result, isLoading = false)
                }
            } catch (e: Exception) {
                addLog("Error: ${e.message}")
                _uiState.update {
                    if (it.selectedMode == SigilMode.AUTO) it.copy(autoOutput = "Encryption Failed", isLoading = false)
                    else it.copy(customOutput = "Encryption Failed", isLoading = false)
                }
                e.printStackTrace()
            }
        }
    }

    fun onDecrypt() {
        val state = _uiState.value
        val pwd = if (state.selectedMode == SigilMode.AUTO) state.autoPassword else state.customPassword
        val input = if (state.selectedMode == SigilMode.AUTO) state.autoInput else state.customInput

        if (pwd.isEmpty()) {
            addLog("Error: Password required for decryption.")
            return
        }
        if (input.isEmpty()) {
            addLog("Warning: No input text found.")
            return
        }

        _uiState.update { it.copy(isLoading = true) }
        addLog("Decryption process started.")

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val decrypted = CryptoEngine.decrypt(
                    encryptedData = input,
                    password = pwd,
                    logCallback = { addLog(it) }
                )

                _uiState.update {
                    if (it.selectedMode == SigilMode.AUTO) it.copy(autoOutput = decrypted, isLoading = false)
                    else it.copy(customOutput = decrypted, isLoading = false)
                }

            } catch (e: Exception) {
                _uiState.update {
                    if (it.selectedMode == SigilMode.AUTO) it.copy(autoOutput = "Decryption Failed", isLoading = false)
                    else it.copy(customOutput = "Decryption Failed", isLoading = false)
                }
                addLog("Error: ${e.message}")
            }
        }
    }
}