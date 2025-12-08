package dev.animeshvarma.sigil

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.AndroidViewModel
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.LayerEntry
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.data.KeystoreRepository
import dev.animeshvarma.sigil.data.VaultEntry
import dev.animeshvarma.sigil.util.SecureMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SigilViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = KeystoreRepository(application)
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _vaultEntries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val vaultEntries: StateFlow<List<VaultEntry>> = _vaultEntries

    init {
        refreshVault()
    }

    private fun refreshVault() {
        _vaultEntries.value = repository.getEntries()
    }

    // --- INPUT HANDLERS ---
    fun onInputTextChanged(newText: String) {
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoInput = newText)
            else it.copy(customInput = newText)
        }
    }

    fun onPasswordChanged(newPassword: String) {
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoPassword = newPassword)
            else it.copy(customPassword = newPassword)
        }
    }

    // --- NAVIGATION ---
    fun onModeSelected(mode: SigilMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun onScreenSelected(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    // --- LOGS ---
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

    // --- CUSTOM LAYER MANAGEMENT ---
    fun addLayer(algo: CryptoEngine.Algorithm) {
        _uiState.update {
            val newEntry = LayerEntry(algorithm = algo)
            it.copy(customLayers = it.customLayers + newEntry)
        }
    }

    fun addLayers(algos: List<CryptoEngine.Algorithm>) {
        _uiState.update {
            val newEntries: List<LayerEntry> = algos.map { algo -> LayerEntry(algorithm = algo) }
            it.copy(customLayers = it.customLayers + newEntries)
        }
    }

    fun removeLayer(index: Int) {
        val mutable = _uiState.value.customLayers.toMutableList()
        if (index in mutable.indices) {
            mutable.removeAt(index)
            _uiState.update { it.copy(customLayers = mutable) }
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

    fun toggleCompression(enabled: Boolean) {
        _uiState.update { it.copy(isCompressionEnabled = enabled) }
    }

    // --- INTENT HANDLING ---
    fun handleIncomingSharedText(text: String) {
        if (text.isNotBlank()) {
            _uiState.update {
                it.copy(
                    autoInput = text,
                    customInput = text
                )
            }
            addLog("Received shared text from external app.")
        }
    }

    // --- CRYPTO OPERATIONS ---
    fun onEncrypt() {
        val state = _uiState.value
        val pwdString = if (state.selectedMode == SigilMode.AUTO) state.autoPassword else state.customPassword
        val input = if (state.selectedMode == SigilMode.AUTO) state.autoInput else state.customInput

        if (pwdString.isEmpty()) {
            addLog("Error: Password is required.")
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val pwdChars = pwdString.toCharArray()
            try {
                val chain = if (state.selectedMode == SigilMode.AUTO) {
                    listOf(CryptoEngine.Algorithm.AES_GCM, CryptoEngine.Algorithm.TWOFISH_CBC, CryptoEngine.Algorithm.SERPENT_CBC).shuffled()
                } else { state.customLayers.map { it.algorithm } }

                val result = CryptoEngine.encrypt(
                    data = input,
                    password = pwdChars,
                    algorithms = chain,
                    compress = state.isCompressionEnabled,
                    logCallback = { addLog(it) }
                )

                // Update UI
                _uiState.update {
                    if (state.selectedMode == SigilMode.AUTO) it.copy(autoOutput = result, isLoading = false)
                    else it.copy(customOutput = result, isLoading = false)
                }

            } catch (e: Exception) {
                addLog("Error: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            } finally {
                SecureMemory.wipe(pwdChars)
            }
        }
    }

    fun onDecrypt() {
        val state = _uiState.value
        val pwdString = if (state.selectedMode == SigilMode.AUTO) state.autoPassword else state.customPassword
        val input = if (state.selectedMode == SigilMode.AUTO) state.autoInput else state.customInput

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val pwdChars = pwdString.toCharArray()
            try {
                val decrypted = CryptoEngine.decrypt(
                    encryptedData = input,
                    password = pwdChars, // Pass CharArray
                    logCallback = { addLog(it) }
                )

                _uiState.update {
                    if (state.selectedMode == SigilMode.AUTO) it.copy(autoOutput = decrypted, isLoading = false)
                    else it.copy(customOutput = decrypted, isLoading = false)
                }
            } catch (e: Exception) {
                addLog("Decryption Error: ${e.message}")
                _uiState.update { it.copy(isLoading = false) }
            } finally {
                SecureMemory.wipe(pwdChars)
            }
        }
    }

    // --- VAULT OPERATIONS ---
    fun saveToVault(password: String) {
        if (password.isEmpty()) {
            addLog("Error: Cannot save empty key.")
            return
        }

        val entropy = SecureMemory.calculateEntropy(password)
        val alias = "Key_${System.currentTimeMillis() / 1000}"

        repository.saveToVault(alias, password, entropy.score, entropy.label)
        refreshVault()
        addLog("Key saved to Hardware Vault as '$alias'.")
    }

    fun loadFromVault(entry: VaultEntry) {
        val secret = repository.loadFromVault(entry.alias)
        if (secret != null) {
            onPasswordChanged(secret)
            addLog("Key '${entry.alias}' loaded securely.")
            // Note: In v0.4 we will avoid putting this in String state
        } else {
            addLog("Error: Failed to decrypt key from Vault.")
        }
    }

    fun deleteFromVault(alias: String) {
        repository.deleteEntry(alias)
        refreshVault()
        addLog("Key '$alias' destroyed.")
    }
}