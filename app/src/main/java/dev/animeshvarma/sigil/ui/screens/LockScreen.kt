@file:Suppress("AssignedValueIsNeverRead")

package dev.animeshvarma.sigil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.model.LockType
import dev.animeshvarma.sigil.ui.components.SecureAlertDialog
import dev.animeshvarma.sigil.util.BiometricHelper

/**
 * Render the Sigil lock screen and manage PIN and biometric authentication flows.
 *
 * This composable displays either a PIN/password entry UI or a biometric unlock button depending on
 * the configured lock mode and runtime state, verifies PIN via the provided ViewModel,
 * triggers biometric prompts (including automatic prompt on lifecycle resume when applicable),
 * and shows dialogs for biometric invalidation and emergency reset. On successful authentication
 * it invokes the provided unlock callback.
 *
 * @param viewModel The SigilViewModel used to read preferences, verify the app secret, and perform data wipe.
 * @param onUnlock Callback invoked when authentication succeeds (PIN verified or biometric success).
 */
@Composable
fun LockScreen(
    viewModel: SigilViewModel,
    onUnlock: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val prefs = viewModel.getPrefs()
    val uiState by viewModel.uiState.collectAsState()

    val lockMode = prefs.lockMode
    val lockType = prefs.lockType

    val incorrectAuthText = if (lockType == LockType.PIN) "Incorrect PIN" else "Incorrect Password"

    var pinInput by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isPinFallback by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showBiometricInvalidatedDialog by remember { mutableStateOf(false) }

    // Helper to trigger bio
    fun triggerBiometric() {
        if (context is FragmentActivity) {
            val currentLockType = prefs.lockType
            val negativeText = if (currentLockType == LockType.PIN) "Use Sigil PIN" else "Use Sigil Password"

            BiometricHelper.showPrompt(
                activity = context,
                negativeButtonText = negativeText,
                onSuccess = { _ -> onUnlock() },
                onFailure = {
                    isPinFallback = true
                },
                onError = { error ->
                    showError = true
                    errorMessage = error
                    isPinFallback = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        }
    }

    var biometricInvalidated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (BiometricHelper.hasBiometricChanged()) {
            biometricInvalidated = true
            showBiometricInvalidatedDialog = true
            isPinFallback = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (BiometricHelper.hasBiometricChanged()) {
                    biometricInvalidated = true
                    showBiometricInvalidatedDialog = true
                    isPinFallback = true
                    return@LifecycleEventObserver
                }

                if (lockMode == LockMode.DEVICE &&
                    !showBiometricInvalidatedDialog &&
                    !isPinFallback
                ) {
                    triggerBiometric()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(16.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(Modifier.height(32.dp))

            Text(
                "Sigil Locked",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Identity verification required.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(48.dp))

            if (lockMode == LockMode.CUSTOM || isPinFallback || showBiometricInvalidatedDialog) {
                val kbType = if (lockType == LockType.PIN) KeyboardType.NumberPassword else KeyboardType.Password
                val labelText = if (lockType == LockType.PIN) "Enter Sigil PIN" else "Enter Password"

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        pinInput = it
                        if (showError) {
                            showError = false
                            errorMessage = ""
                        }
                    },
                    label = { Text(labelText) },
                    singleLine = true,
                    isError = showError,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = kbType,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            keyboardController?.hide()
                            if (uiState.isLoading) return@KeyboardActions
                            if (pinInput.isNotEmpty()) {
                                viewModel.verifyAppSecret(pinInput) { isValid ->
                                    if (isValid) {
                                        onUnlock()
                                    } else {
                                        showError = true
                                        errorMessage = incorrectAuthText
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        pinInput = ""
                                    }
                                }
                            }
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (showError) {
                    Text(
                        text = errorMessage.ifEmpty { incorrectAuthText },
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        keyboardController?.hide()
                        if (pinInput.isEmpty()) return@Button
                        viewModel.verifyAppSecret(pinInput) { isValid ->
                            if (isValid) {
                                onUnlock()
                            } else {
                                showError = true
                                errorMessage = incorrectAuthText
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                pinInput = ""
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !uiState.isLoading && pinInput.isNotEmpty()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text("Unlock")
                    }
                }

                // Retry Biometrics Button (Only if valid)
                if (lockMode == LockMode.DEVICE && isPinFallback && !biometricInvalidated) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { triggerBiometric() }) {
                        Icon(Icons.Default.Fingerprint, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Try Biometrics Again")
                    }
                }

            } else {
                Button(
                    onClick = { triggerBiometric() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.LockOpen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Unlock with Biometrics")
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = { showWipeDialog = true }) {
                val forgotText = if (lockType == LockType.PIN) "Forgot PIN? Reset App" else "Forgot Password? Reset App"
                Text(forgotText, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    // --- DIALOGS ---
    if (showBiometricInvalidatedDialog) {
        SecureAlertDialog(
            onDismissRequest = {
            },
            icon = {
                Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
            },
            title = {
                Text("Security Alert")
            },
            text = {
                val authType = if (lockType == LockType.PIN) "PIN" else "password"
                Text("A change in your device's biometric settings was detected. \n\nFor your security, Sigil has fallen back to $authType authentication. You will need to re-enable Biometric Unlock in Sigil Settings after logging in.")
            },
            confirmButton = {
                TextButton(
                    onClick = { showBiometricInvalidatedDialog = false }
                ) { Text("I Understand") }
            }
        )
    }

    if (showWipeDialog) {
        SecureAlertDialog(
            onDismissRequest = { showWipeDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Emergency Reset") },
            text = { Text("This will delete ALL data, including your Vault keys, Settings, and PIN. This action is irreversible.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.wipeAllData() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Wipe Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancel") }
            }
        )
    }
}