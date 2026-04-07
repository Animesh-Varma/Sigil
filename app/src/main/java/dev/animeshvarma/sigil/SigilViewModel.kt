package dev.animeshvarma.sigil

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.core.content.edit
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.data.KeystoreRepository
import dev.animeshvarma.sigil.data.LockManager
import dev.animeshvarma.sigil.data.VaultEntry
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.EncryptionProfile
import dev.animeshvarma.sigil.model.LayerEntry
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.model.LockType
import dev.animeshvarma.sigil.model.ProfileRegistry
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.util.SecureMemory
import dev.animeshvarma.sigil.util.SigilPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.system.exitProcess
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SigilViewModel(application: Application) : AndroidViewModel(application) {

    private val repository by lazy { KeystoreRepository(application) }
    private val prefs = SigilPreferences(application)
    private val _uiState = MutableStateFlow(UiState())
    private val lockManager = LockManager(application)

    val uiState: StateFlow<UiState> = _uiState
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Vault State
    private val _vaultEntries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val vaultEntries: StateFlow<List<VaultEntry>> = _vaultEntries

    private var pendingSharedText: String? = null

    private var currentViewModelToast: Toast? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            refreshVault()
            loadProfiles()
        }
    }

    /**
     * Displays a short Android Toast on the main thread, cancelling any previously shown toast.
     *
     * @param message The text to display in the toast.
     */
    private fun showBackgroundToast(message: String) {
        viewModelScope.launch(Dispatchers.Main) {
            currentViewModelToast?.cancel()
            val toast = Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT)
            currentViewModelToast = toast
            toast.show()
        }
    }

    /**
     * Loads custom profiles from preferences, combines them with built-in profiles, selects the active profile
     * (falling back to the default if the saved id is missing), and updates the UI state with the available and active profiles.
     */

    private fun loadProfiles() {
        val customProfiles = prefs.getCustomProfiles()
        val allProfiles = ProfileRegistry.builtInProfiles + customProfiles

        val savedId = prefs.activeProfileId
        val active = allProfiles.find { it.id == savedId } ?: ProfileRegistry.defaultProfile
        if (savedId != active.id) {
            prefs.activeProfileId = active.id
        }

        _uiState.update {
            it.copy(
                availableProfiles = allProfiles,
                activeProfile = active
            )
        }
    }

    /**
     * Sets the given encryption profile as the application's active profile.
     *
     * Updates the persistent active profile preference, updates the UI state to reflect the selection,
     * and records the profile switch in the view model logs.
     *
     * @param profile The EncryptionProfile to select as active.
     */
    fun selectProfile(profile: EncryptionProfile) {
        prefs.activeProfileId = profile.id
        _uiState.update { it.copy(activeProfile = profile) }
        addLog("Profile Switched: ${profile.name}")
    }

    /**
     * Creates and persists a new custom encryption profile, then activates it.
     *
     * Validates that `name` is not blank and `layers` is not empty; if a profile with the same
     * name (case-insensitive) already exists, the `onDuplicateName` callback is invoked instead.
     *
     * @param name The display name for the new profile.
     * @param description Optional human-readable description for the profile.
     * @param layers Ordered list of algorithms that form the encryption chain for this profile.
     * @param kdfOverride Optional KDF configuration to use for this profile instead of the global KDF settings.
     * @param compress Whether compression should be enabled when using this profile.
     * @param isRaw If true, indicates the profile represents a raw single-layer mode rather than a container chain.
     * @param onSuccess Callback invoked after the profile is saved and selected as the active profile.
     * @param onDuplicateName Callback invoked with the existing conflicting profile when a duplicate name is detected.
     */
    fun saveProfile(
        name: String,
        description: String,
        layers: List<CryptoEngine.Algorithm>,
        kdfOverride: CryptoEngine.KdfConfig?,
        compress: Boolean,
        isRaw: Boolean,
        onSuccess: () -> Unit,
        onDuplicateName: (EncryptionProfile) -> Unit
    ) {
        if (name.isBlank()) {
            addLog("Error: Profile name required.")
            return
        }
        if (layers.isEmpty()) {
            addLog("Error: Cannot save empty chain.")
            return
        }

        val currentCustom = prefs.getCustomProfiles().toMutableList()
        val allProfiles = ProfileRegistry.builtInProfiles + currentCustom

        // Check for Name Duplication (including built-in profiles)
        val duplicate = allProfiles.find { it.name.equals(name, ignoreCase = true) }
        if (duplicate != null) {
            onDuplicateName(duplicate)
            return
        }

        val newProfile = EncryptionProfile(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            layers = layers,
            kdfConfig = kdfOverride,
            isCompressionEnabled = compress,
            isBuiltIn = false,
            isRaw = isRaw
        )

        currentCustom.add(newProfile)
        prefs.saveCustomProfiles(currentCustom)
        loadProfiles()
        selectProfile(newProfile)
        onSuccess()
        addLog("Profile Saved: $name")
    }

    /**
     * Updates an existing custom profile by ID with new configuration.
     *
     * @param id The ID of the profile to update.
     * @param name The new display name.
     * @param description The new description.
     * @param layers The new list of algorithms.
     * @param kdfOverride The new KDF configuration override (or null).
     * @param compress Whether compression is enabled.
     * @param isRaw Whether raw mode is enabled.
     * @param onSuccess Callback invoked after successful update.
     */
    fun updateExistingProfile(
        id: String,
        name: String,
        description: String,
        layers: List<CryptoEngine.Algorithm>,
        kdfOverride: CryptoEngine.KdfConfig?,
        compress: Boolean,
        isRaw: Boolean,
        onSuccess: () -> Unit,
        onNameCollision: () -> Unit = {}
    ) {
        if (name.isBlank()) {
            addLog("Error: Profile name required.")
            return
        }
        if (layers.isEmpty()) {
            addLog("Error: Cannot save empty chain.")
            return
        }

        val currentCustom = prefs.getCustomProfiles().toMutableList()
        val index = currentCustom.indexOfFirst { it.id == id }

        if (index != -1) {
            val allProfiles = ProfileRegistry.builtInProfiles + currentCustom
            val collision = allProfiles.any { it.id != id && it.name.equals(name, ignoreCase = true) }
            if (collision) {
                addLog("Error: Profile name '$name' is already taken.")
                onNameCollision()
                return
            }

            val updated = currentCustom[index].copy(
                name = name,
                description = description,
                layers = layers,
                kdfConfig = kdfOverride,
                isCompressionEnabled = compress,
                isRaw = isRaw
            )
            currentCustom[index] = updated
            prefs.saveCustomProfiles(currentCustom)
            loadProfiles()
            selectProfile(updated)
            onSuccess()
            addLog("Profile Updated: $name")
        } else {
            addLog("Error: Profile not found for update.")
        }
    }

    /**
     * Replaces an existing custom encryption profile with the provided profile (matched by name, case-insensitive) and applies it as active.
     *
     * If a custom profile with the same name exists, this updates the stored custom profiles, reloads available profiles, selects the updated profile, and records a log entry.
     *
     * @param profile The encryption profile to save in place of an existing custom profile with the same name.
     */
    fun overwriteProfile(profile: EncryptionProfile) {
        val currentCustom = prefs.getCustomProfiles().toMutableList()
        val index = currentCustom.indexOfFirst { it.name.equals(profile.name, ignoreCase = true) }
        if (index != -1) {
            currentCustom[index] = profile
            prefs.saveCustomProfiles(currentCustom)
            loadProfiles()
            selectProfile(profile)
            addLog("Profile Updated: ${profile.name}")
        } else {
            addLog("Error: Profile '${profile.name}' not found for overwrite.")
        }
    }

    /**
     * Deletes a custom encryption profile identified by `profileId`.
     *
     * If a matching custom profile exists it is removed from stored custom profiles, the profiles are reloaded,
     * and the default profile is selected if the deleted profile was active. Also records a log entry when deletion occurs.
     *
     * @param profileId The identifier of the custom profile to delete.
     */
    fun deleteProfile(profileId: String) {
        val currentCustom = prefs.getCustomProfiles().toMutableList()
        if (currentCustom.removeIf { it.id == profileId }) {
            prefs.saveCustomProfiles(currentCustom)
            loadProfiles()
            addLog("Profile Deleted.")
        }
    }

    /**
     * Retrieve an encryption profile by its identifier.
     *
     * @param id The profile identifier to look up.
     * @return The matching `EncryptionProfile` if found, `null` otherwise.
     */
    fun getProfileById(id: String): EncryptionProfile? {
        return _uiState.value.availableProfiles.find { it.id == id }
    }

    /**
     * Returns the effective KDF configuration: prefer an active profile's override when in AUTO mode, otherwise use global preferences.
     *
     * @return The `CryptoEngine.KdfConfig` from the active profile if available in AUTO mode; otherwise a config built from stored preferences (`prefs.kdfIterations`, `prefs.kdfMemoryPow2`, `prefs.kdfParallelism`).
     */
    private fun getActiveKdfConfig(): CryptoEngine.KdfConfig {
        // 1. Check if ACTIVE PROFILE has an override
        if (_uiState.value.selectedMode == SigilMode.AUTO) {
            val profileKdf = _uiState.value.activeProfile.kdfConfig
            if (profileKdf != null) return profileKdf
        }

        // 2. Fallback to Global Settings
        return CryptoEngine.KdfConfig(
            iterations = prefs.kdfIterations,
            memoryPow2 = prefs.kdfMemoryPow2,
            parallelism = prefs.kdfParallelism
        )
    }

    fun cachePendingIntent(text: String) {
        pendingSharedText = text
        addLog("Incoming data cached (Waiting for unlock).")
    }

    fun consumePendingIntent() {
        pendingSharedText?.let { text ->
            handleIncomingSharedText(text)
            pendingSharedText = null
        }
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

    /**
     * Selects the active encryption mode and updates the UI state.
     *
     * When switching to SigilMode.CUSTOM, clears the current editing profile ID so no profile is considered
     * being edited.
     *
     * @param mode The SigilMode to select; if `SigilMode.CUSTOM`, the editing profile ID is cleared.
     */
    fun onModeSelected(mode: SigilMode) {
        _uiState.update {
            it.copy(
                selectedMode = mode,
                editingProfileId = if (mode == SigilMode.CUSTOM) null else it.editingProfileId
            )
        }
    }

    /**
     * Load the given encryption profile into the custom editor and switch the UI to custom mode.
     *
     * Populates the custom layer list, applies the profile's compression setting, marks the profile as
     * being edited, and records the action in the logs.
     *
     * @param profile The EncryptionProfile to load into the custom editor.
     */
    fun loadProfileToCustomMode(profile: EncryptionProfile) {
        _uiState.update {
            it.copy(
                customLayers = profile.layers.map { algo -> LayerEntry(algorithm = algo) },
                isCompressionEnabled = profile.isCompressionEnabled,
                editingProfileId = profile.id,
                selectedMode = SigilMode.CUSTOM
            )
        }
        addLog("Editing Profile: '${profile.name}'")
    }

    /**
     * Sets the active application screen in the view model's UI state.
     *
     * @param screen The screen to set as the current view.
     */
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
    fun addLayers(algos: List<CryptoEngine.Algorithm>) {
        _uiState.update { state ->
            state.copy(
                customLayers = state.customLayers + algos.map { algo ->
                    LayerEntry(algorithm = algo)
                }
            )
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
            _uiState.update { it.copy(autoInput = text, customInput = text) }
            addLog("Received shared text from external app.")
        }
    }

    /**
     * Encrypts the current input using the selected mode and updates the UI state with the resulting output.
     *
     * If the selected mode is AUTO and the active profile is marked RAW, performs a single-layer raw encryption using that profile's algorithm; otherwise builds and encrypts a Sigil container using the active profile's layers (AUTO) or the user-defined layers (CUSTOM). Requires a non-empty password; if the password is empty the operation is aborted and a log entry is added. While running, sets the view model loading flag and clears the password fields from the UI state. On completion or failure updates the appropriate output field and clears the loading flag, logs progress and errors, and securely wipes password memory.
     */
    fun onEncrypt() {
        val state = _uiState.value
        val pwdString = if (state.selectedMode == SigilMode.AUTO) state.autoPassword else state.customPassword
        val input = if (state.selectedMode == SigilMode.AUTO) state.autoInput else state.customInput

        if (pwdString.isEmpty()) {
            addLog("Error: Encryption aborted. Password is required.")
            return
        }

        // TRIGGER LOADING
        _uiState.update { it.copy(isLoading = true) }

        // Wipe password from UI state
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoPassword = "")
            else it.copy(customPassword = "")
        }

        addLog("Encryption process started.")

        viewModelScope.launch(Dispatchers.IO) {
            val pwdChars = pwdString.toCharArray()
            try {
                val result: String
                val kdfConfig = getActiveKdfConfig()

                // CHECK FOR RAW PROFILE IN AUTO MODE
                if (state.selectedMode == SigilMode.AUTO && state.activeProfile.isRaw) {
                    val profile = state.activeProfile
                    // Fallback to AES_GCM is defensive; profile validation ensures layers is non-empty
                    val algo = profile.layers.firstOrNull() ?: CryptoEngine.Algorithm.AES_GCM

                    addLog("Mode: Raw (${algo.name}).")
                    result = CryptoEngine.encryptRaw(
                        data = input.toByteArray(StandardCharsets.UTF_8),
                        password = pwdChars,
                        algorithm = algo,
                        kdfConfig = kdfConfig,
                        logCallback = { addLog(it) }
                    )
                } else {
                    // SIGIL CONTAINER (Auto or Custom)
                    val chain: List<CryptoEngine.Algorithm>
                    val compress: Boolean

                    if (state.selectedMode == SigilMode.AUTO) {
                        val profile = state.activeProfile
                        chain = profile.layers
                        compress = profile.isCompressionEnabled
                        addLog("Using Profile: ${profile.name}")
                    } else {
                        chain = state.customLayers.map { it.algorithm }
                        compress = state.isCompressionEnabled
                    }

                    if (chain.isEmpty()) throw IllegalStateException("No encryption layers selected.")

                    result = CryptoEngine.encrypt(
                        data = input.toByteArray(StandardCharsets.UTF_8),
                        password = pwdChars,
                        algorithms = chain,
                        kdfConfig = kdfConfig,
                        compress = compress,
                        logCallback = { addLog(it) }
                    )
                }
                val wasAutoMode = state.selectedMode == SigilMode.AUTO
                _uiState.update {
                    if (wasAutoMode) it.copy(autoOutput = result, isLoading = false)
                    else it.copy(customOutput = result, isLoading = false)
                }
            } catch (e: Exception) {
                addLog("Error: Encryption failed - ${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
                _uiState.update { it.copy(isLoading = false) }
            } finally {
                SecureMemory.wipe(pwdChars)
            }
        }
    }

    /**
     * Performs decryption of the currently selected input using the active profile and KDF settings.
     *
     * If no password or input is present, the method logs a message and returns without changing state.
     * The UI loading state is set while decryption runs; the password is cleared from UI state immediately.
     * Decryption runs on a background IO coroutine and:
     * - Uses raw decryption when the active profile is marked `isRaw`, otherwise treats input as a Sigil container.
     * - Writes the decrypted UTF-8 string to the appropriate output field (autoOutput or customOutput) and clears the loading flag on success.
     * - On failure, writes a contextual, user-facing error message to the output and clears the loading flag.
     *
     * Sensitive data (password characters and decrypted bytes) is wiped from memory after use. Activity and errors are recorded via the view model's logging facility.
     */
    fun onDecrypt() {
        val state = _uiState.value
        val pwdString = if (state.selectedMode == SigilMode.AUTO) state.autoPassword else state.customPassword
        val input = if (state.selectedMode == SigilMode.AUTO) state.autoInput else state.customInput

        if (pwdString.isEmpty()) {
            addLog("Error: Password required for decryption.")
            return
        }
        if (input.isEmpty()) {
            addLog("Warning: No input text found.")
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        // Wipe password from UI state
        _uiState.update {
            if (it.selectedMode == SigilMode.AUTO) it.copy(autoPassword = "")
            else it.copy(customPassword = "")
        }

        addLog("Decryption process started.")

        viewModelScope.launch(Dispatchers.IO) {
            val pwdChars = pwdString.toCharArray()
            try {
                val kdfConfig = getActiveKdfConfig()
                val decryptedBytes: ByteArray

                // CHECK FOR RAW PROFILE
                if (state.selectedMode == SigilMode.AUTO && state.activeProfile.isRaw) {
                    val profile = state.activeProfile
                    val algo = profile.layers.firstOrNull() ?: CryptoEngine.Algorithm.AES_GCM

                    addLog("Mode: Raw Decryption (${algo.name}). Expecting raw container.")
                    decryptedBytes = CryptoEngine.decryptRaw(
                        encryptedData = input,
                        password = pwdChars,
                        algorithm = algo,
                        kdfConfig = kdfConfig,
                        logCallback = { addLog(it) }
                    )
                } else {
                    // SIGIL CONTAINER
                    decryptedBytes = CryptoEngine.decrypt(
                        encryptedData = input,
                        password = pwdChars,
                        kdfConfig = kdfConfig,
                        logCallback = { addLog(it) }
                    )
                }

                val decryptedString = String(decryptedBytes, StandardCharsets.UTF_8)
                SecureMemory.wipe(decryptedBytes)

                _uiState.update {
                    if (it.selectedMode == SigilMode.AUTO) it.copy(
                        autoOutput = decryptedString,
                        isLoading = false
                    )
                    else it.copy(customOutput = decryptedString, isLoading = false)
                }

            } catch (e: Exception) {
                val errorReport = StringBuilder()
                if (state.selectedMode == SigilMode.AUTO && state.activeProfile.isRaw) {
                    errorReport.append("Raw Decryption Failed\n")
                    errorReport.append("--------------------------\n")
                    errorReport.append("Integrity validation is disabled in Raw mode. Please manually ensure the password, profile, and data are correct.")
                } else {
                    errorReport.append("Decryption Failed\n")
                    errorReport.append("--------------------------\n")
                    errorReport.append("The system could not decrypt the data. Please verify the following:\n")
                    errorReport.append(" • Credentials: Is the password/key correct?\n")
                    errorReport.append(" • Integrity: The data may be corrupted or truncated.\n")
                    errorReport.append(" • Security Parameters: Check KDF iterations and memory limits.\n")
                    errorReport.append(" • Profile Context: Ensure you are using the correct profile.\n")
                }

                val finalMessage = errorReport.toString()

                _uiState.update {
                    if (it.selectedMode == SigilMode.AUTO) it.copy(
                        autoOutput = finalMessage,
                        isLoading = false
                    )
                    else it.copy(customOutput = finalMessage, isLoading = false)
                }

                addLog("Decryption Failed - ${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
            } finally {
                SecureMemory.wipe(pwdChars)
            }
        }
    }

    // --- VAULT OPERATIONS ---
    fun saveToVault(alias: String, password: String) {
        if (alias.isBlank()) {
            addLog("Error: Key name cannot be empty.")
            return
        }
        if (password.isEmpty()) {
            addLog("Error: Cannot save empty key.")
            return
        }

        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val pwdChars = password.toCharArray()
            try {
                val entropy = SecureMemory.calculateEntropy(password)
                repository.saveToVault(alias, pwdChars, entropy.score, entropy.label)
                refreshVault()
                addLog("Key saved to Vault as '$alias'.")
            } finally {
                SecureMemory.wipe(pwdChars)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun loadFromVault(entry: VaultEntry) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val secret = repository.loadFromVault(entry.alias)
            if (secret != null) {
                onPasswordChanged(secret)
                addLog("Key '${entry.alias}' loaded.")
            } else {
                addLog("Error: Failed to decrypt key.")
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun deleteFromVault(alias: String) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEntry(alias)
            refreshVault()
            addLog("Key '$alias' deleted.")
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun renameVaultEntry(oldAlias: String, newAlias: String) {
        if (newAlias.isBlank()) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.renameEntry(oldAlias, newAlias)) {
                refreshVault()
                addLog("Renamed '$oldAlias' to '$newAlias'.")
            } else {
                addLog("Error: Rename failed.")
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Loads the secret associated with a vault entry alias and delivers it to the caller.
     *
     * Updates the UI loading state while the vault is accessed and invokes `onResult` on the main thread
     * with the secret value or `null` if the entry is not found.
     *
     * @param alias The vault entry alias to load.
     * @param onResult Callback invoked with the secret value for `alias`, or `null` if unavailable.
     */
    fun viewKey(alias: String, onResult: (String?) -> Unit) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val secret = repository.loadFromVault(alias)
            _uiState.update { it.copy(isLoading = false) }
            withContext(Dispatchers.Main) {
                onResult(secret)
            }
        }
    }

    // --- DEMO / ONBOARDING CONTROL ---
    // 1. Control the Docs Tab programmatically
    private val _demoDocsTabIndex = MutableStateFlow(0)
    val demoDocsTabIndex: StateFlow<Int> = _demoDocsTabIndex

    fun setDemoMode(active: Boolean) {
        if (!active) {
            clearSensitiveData()
            refreshVault()
            toggleDemoDropdown(false)
        } else {
            _demoDocsTabIndex.value = 0
        }
    }

    fun toggleDemoDropdown(show: Boolean) {
        _uiState.update { it.copy(isDemoDropdownExpanded = show) }
    }

    fun setDocsTab(index: Int) {
        _demoDocsTabIndex.value = index
    }

    // 2. Simulate Layer Reordering for Advanced Demo
    fun demoSwapLayers() {
        val current = _uiState.value.customLayers.toMutableList()
        if (current.size >= 2) {
            val temp = current[0]
            current[0] = current[1]
            current[1] = temp
            _uiState.update { it.copy(customLayers = current) }
        }
    }

    // 3. Inject Fake Data
    fun injectDemoData(input: String, pass: String, output: String = "") {
        _uiState.update {
            it.copy(
                autoInput = input,
                autoPassword = pass,
                autoOutput = output,
                customInput = input,
                customPassword = pass
            )
        }
    }

    fun toggleDemoDrawer(open: Boolean) {
        _uiState.update { it.copy(isDemoDrawerOpen = open) }
    }

    fun injectDemoVault() {
        _vaultEntries.value = listOf(
            VaultEntry("Key 1", System.currentTimeMillis(), 100, "Unbreakable"),
            VaultEntry("Key 2", System.currentTimeMillis() - 1000000, 75, "Strong"),
            VaultEntry("Key 3", System.currentTimeMillis() - 5000000, 40, "Moderate")
        )
    }

    // --- SETTINGS OPERATIONS ---

    fun isOnboardingReset(): Boolean {
        return !prefs.hasCompletedOnboarding()
    }

    fun setOnboardingReset(reset: Boolean) {
        prefs.setOnboardingCompleted(!reset)
        if (reset) {
            addLog("Onboarding enabled for next session.")
        } else {
            addLog("Onboarding disabled.")
        }
    }
    // --- SECURITY SETTINGS ---
    fun setLockMode(mode: LockMode) {
        prefs.lockMode = mode
    }

    /**
     * Sets the application lock (PIN or Password) using the LockManager.
     * This replaces the old setCustomPin method.
     *
     * @param secret The plaintext secret (PIN or Password).
     * @param type The type of lock (used to determine keyboard layout on LockScreen).
     * @param onResult Callback indicating success or failure.
     */
    fun setAppLock(secret: String, type: LockType, onResult: (Boolean) -> Unit) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            var success = false
            try {
                lockManager.setAppLock(secret, type)

                withContext(Dispatchers.Main) {
                    addLog("App secret set (TEE Encrypted).")
                    addLog("App Lock enabled (${type.name}).")
                }
                success = true
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog("Error: Failed to enable App Lock - ${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
                }
                success = false
            } finally {
                _uiState.update { it.copy(isLoading = false) }
                withContext(Dispatchers.Main) {
                    onResult(success)
                }
            }
        }
    }

    /**
     * Verifies the provided input against the stored Argon2 hash.
     * Renamed from verifyAppPin to verifyAppSecret to support Passwords.
     */
    fun verifyAppSecret(input: String, onResult: (Boolean) -> Unit) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val isValid = try {
                lockManager.verifySecret(input)
            } catch (e: Exception) {
                addLog("Error: App lock verification failed - ${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}")
                false
            }

            if (!isValid) delay(1000)

            _uiState.update { it.copy(isLoading = false) }

            withContext(Dispatchers.Main) {
                onResult(isValid)
            }
        }
    }

    /**
     * Performs a full application data wipe and restarts the app.
     *
     * Deletes the app's shared preferences, removes entries from the Android Keystore,
     * and deletes internal cache and files. After cleanup the app is restarted and the
     * current process is terminated. Internal errors during wipe operations are caught
     * and do not prevent the restart.
     */
    fun wipeAllData() {
        // TRIGGER LOADING STATE
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()

            // 1. WIPE ALL PREFERENCES (Using delete for complete removal)
            try {
                context.deleteSharedPreferences("sigil_prefs")
                context.deleteSharedPreferences("sigil_vault_v3")
                context.deleteSharedPreferences("sigil_auth")
            } catch (_: Exception) {
                // Fallback for older APIs or weird file locks
                context.getSharedPreferences("sigil_prefs", Context.MODE_PRIVATE).edit { clear() }
                context.getSharedPreferences("sigil_vault_v3", Context.MODE_PRIVATE).edit { clear() }
                context.getSharedPreferences("sigil_auth", Context.MODE_PRIVATE).edit { clear() }
            }

            // 2. WIPE HARDWARE KEYSTORE (TEE)
            try {
                val ks = java.security.KeyStore.getInstance("AndroidKeyStore")
                ks.load(null)
                val aliasesList = ks.aliases().toList()
                for (alias in aliasesList) {
                    ks.deleteEntry(alias)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. WIPE INTERNAL STORAGE
            try {
                context.cacheDir.deleteRecursively()
                context.filesDir.deleteRecursively()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4. RESTART APP
            withContext(Dispatchers.Main) {
                val packageManager = context.packageManager
                val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                val componentName = intent?.component
                val mainIntent = Intent.makeRestartActivityTask(componentName)
                context.startActivity(mainIntent)
                exitProcess(0)
            }
        }
    }

    /**
     * Toggle use of dynamic system-derived colors for the app theme.
     *
     * @param enabled `true` to enable dynamic colors (use system-derived palette), `false` to disable them (use app-defined colors).
     */
    fun setDynamicColors(enabled: Boolean) { prefs.isDynamicColorsEnabled = enabled }
    /**
     * Sets whether the app should use dark theme.
     *
     * @param enabled `true` to enable dark mode, `false` to disable it.
     */
    fun setDarkMode(enabled: Boolean) { prefs.isDarkModeEnabled = enabled }
    /**
     * Updates the application's selected theme color and persists it in preferences.
     *
     * The color is stored as an ARGB integer in persistent settings.
     *
     * @param color The new theme color to apply and save.
     */
    fun setThemeColor(color: Color) {
        prefs.selectedThemeColor = color.toArgb()
    }

    fun getPrefs() = prefs

    fun hasAppLockSet(): Boolean {
        return lockManager.hasAppLockSet()
    }

    /**
     * Copy sensitive text to the system clipboard and schedule an automatic wipe.
     *
     * If `text` is blank the function returns immediately and does nothing. The copied clip
     * is marked as sensitive on Android 13+ so the platform may treat it accordingly. A background
     * toast and a log entry are produced when the text is copied and again when the clipboard is wiped.
     * The clipboard is cleared after the configured clipboard timeout; exceptions raised during the
     * wipe are ignored.
     *
     * @param text The sensitive text to copy to the clipboard.
     * @param label A human-readable label for the clipboard entry; defaults to "Sigil Content".
     */
    fun copyToClipboardSecurely(text: String, label: String = "Sigil Content") {
        if (text.isBlank()) return

        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        // 1. Create ClipData
        val clip = ClipData.newPlainText(label, text)

        // 2. Android 13+ Sensitive Flag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }

        clipboard.setPrimaryClip(clip)

        // 3. User Feedback & Timer
        val timeout = prefs.clipboardTimeoutSeconds

        showBackgroundToast("Copied! Wiping in ${timeout}s")
        addLog("Sensitive data copied. Wiping in ${timeout}s.")

        // 4. Schedule Auto-Wipe
        viewModelScope.launch(Dispatchers.Main) {
            delay(timeout * 1000L)

            try {
                if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.label == label) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }

                    showBackgroundToast("Sigil: Clipboard auto-wiped.")
                    addLog("Clipboard auto-wiped.")
                }
            } catch (e: Exception) {
                android.util.Log.d("SigilViewModel", "Clipboard auto-wipe failed: ${e.message}")
            }

        }
    }

    fun clearClipboardSecurely() {
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            showBackgroundToast("Clipboard wiped (Security Alert)")
            addLog("Clipboard wiped due to security alert.")
        } catch (e: Exception) {
            android.util.Log.d("SigilViewModel", "Clipboard wipe failed: ${e.message}")
        }
    }

    private val exemptDialogs = MutableStateFlow<Set<String>>(emptySet())

    val isSecureExempt: StateFlow<Boolean> = exemptDialogs
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    fun requestExemption(id: String) {
        exemptDialogs.update { it + id }
    }

    fun revokeExemption(id: String) {
        exemptDialogs.update { it - id }
    }

    fun releaseExemption(id: String) {
        viewModelScope.launch {
            delay(50)
            revokeExemption(id)
        }
    }

    private val _isScreenRecording = MutableStateFlow(false)
    val isScreenRecording: StateFlow<Boolean> = _isScreenRecording

    fun setScreenRecordingState(isRecording: Boolean) {
        _isScreenRecording.value = isRecording
    }
}