@file:Suppress("AssignedValueIsNeverRead")

package dev.animeshvarma.sigil.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import android.os.Build
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.LockMode
import dev.animeshvarma.sigil.model.LockType
import dev.animeshvarma.sigil.ui.components.SigilSegmentedControl
import dev.animeshvarma.sigil.util.BiometricHelper
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.system.exitProcess

private const val DEFAULT_LABEL = "(Default)"
/**
 * Renders the app Settings screen with controls for general options, encryption parameters, privacy, app lock, appearance, and data management.
 *
 * Displays interactive sections and dialogs for:
 * - General (onboarding reset, restart),
 * - Encryption Parameters (Argon2 settings),
 * - Privacy & Clipboard (screen shield, clipboard auto-wipe),
 * - App Lock (PIN/biometrics, grace period, PIN management),
 * - Appearance (dynamic colors, accent color, dark mode),
 * - Data Management (reset profiles, reset preferences, wipe all data).
 *
 * The UI synchronizes local state with the provided ViewModel and persists preference changes via the ViewModel/prefs. Dialogs handle PIN setup/verification, color selection, and confirmations for destructive actions.
 *
 * @param viewModel The SigilViewModel that provides preferences, performs preference updates, and exposes actions such as resetting profiles, resetting preferences, setting/wiping PINs, and wiping all data.
 */
@Composable
fun SettingsScreen(viewModel: SigilViewModel) {
    val context = LocalContext.current
    val prefs = viewModel.getPrefs()

    // --- STATE HOISTING ---
    var onboardingToggle by remember { mutableStateOf(viewModel.isOnboardingReset()) }

    // Security State
    var lockMode by remember { mutableStateOf(prefs.lockMode) }

    // Grace Period
    var graceEnabled by remember { mutableStateOf(prefs.isGracePeriodEnabled) }
    var graceMinutes by remember { mutableFloatStateOf(prefs.graceDurationMinutes.toFloat()) }

    // Cryptography (KDF)
    var kdfIterations by remember { mutableFloatStateOf(prefs.kdfIterations.toFloat()) }
    var kdfMemory by remember { mutableFloatStateOf(prefs.kdfMemoryPow2.toFloat()) }
    var kdfParallelism by remember { mutableFloatStateOf(prefs.kdfParallelism.toFloat()) }

    // Clipboard
    var clipTimeout by remember { mutableFloatStateOf(prefs.clipboardTimeoutSeconds.toFloat()) }

    // Privacy
    var screenShield by remember { mutableStateOf(prefs.isScreenShieldEnabled) }

    // Device Capabilities
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val systemDark = isSystemInDarkTheme()

    // Appearance State
    var dynamicColors by remember {
        mutableStateOf(supportsDynamicColor && prefs.isDynamicColorsEnabled)
    }
    var darkMode by remember { mutableStateOf(prefs.isDarkModeEnabled) }
    var selectedColorInt by remember { mutableIntStateOf(prefs.selectedThemeColor) }

    LaunchedEffect(Unit) {
        if (!supportsDynamicColor && prefs.isDynamicColorsEnabled) {
            viewModel.setDynamicColors(false)
        }
    }

    // Dialogs
    var showVerifyPinDialog by remember { mutableStateOf(false) }
    var showColorDialog by remember { mutableStateOf(false) }
    var showSecurityErrorDialog by remember { mutableStateOf(false) }

    // Danger Dialogs
    var showResetSettingsDialog by remember { mutableStateOf(false) }
    var showWipeDataDialog by remember { mutableStateOf(false) }

    // --- LOCK SETUP STATE MACHINE ---
    var isSetupFlowActive by remember { mutableStateOf(false) }
    var setupStep by remember { mutableIntStateOf(0) }
    var tempSecret by remember { mutableStateOf("") }
    var selectedLockType by remember { mutableStateOf(LockType.PIN) }
    var isSavingPin by remember { mutableStateOf(false) }

    // Used to queue the target lock mode after PIN is set
    var pendingLockMode by remember { mutableStateOf<LockMode?>(null) }

    // Helper to reset setup state
    val cleanupSetup: (Boolean) -> Unit = { success ->
        isSetupFlowActive = false
        setupStep = 0
        tempSecret = ""
        pendingLockMode = null
        isSavingPin = false

        if (!success && !viewModel.hasAppLockSet()) {
            lockMode = LockMode.NONE
            viewModel.setLockMode(LockMode.NONE)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {

        // --- GENERAL ---
        SettingsHeader("General")

        SettingsItem(
            title = "Show Onboarding",
            desc = "Trigger the introductory tour on the next app launch.",
            trailing = {
                Switch(
                    checked = onboardingToggle,
                    onCheckedChange = {
                        onboardingToggle = it
                        viewModel.setOnboardingReset(it)
                    }
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { restartApp(context) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Restart App")
        }

        Spacer(Modifier.height(24.dp))

        // --- CRYPTOGRAPHY ---
        SettingsHeader("Encryption Parameters (Argon2)")

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {

                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Global defaults (overridable by profiles). These settings must match exactly for encryption and decryption; modify only if you know exactly what you’re doing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Iterations
                val iterVal = kdfIterations.roundToInt()
                val iterLabel = when (iterVal) {
                    10 -> DEFAULT_LABEL
                    in 1..5 -> "(Fast/Weak)"
                    in 25..32 -> "(Max)"
                    else -> ""
                }
                Text("Iterations: $iterVal $iterLabel", fontWeight = FontWeight.Bold)
                Text("Higher = Harder to brute force, slower to decrypt.", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = kdfIterations,
                    onValueChange = { kdfIterations = it },
                    onValueChangeFinished = { prefs.kdfIterations = kdfIterations.roundToInt() },
                    valueRange = 1f..32f,
                    steps = 30
                )

                val memoryMb = (1 shl kdfMemory.toInt()) / 1024
                val memLabel = when (memoryMb) {
                    64 -> DEFAULT_LABEL
                    256 -> "(Max)"
                    else -> ""
                }

                Text("Memory Cost: ${memoryMb}MB $memLabel", fontWeight = FontWeight.Bold)

                // WARNING LOGIC
                if (kdfMemory >= 18f) {
                    Text(
                        "Warning: 256MB is extremely heavy. May crash older devices.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Slider(
                    value = kdfMemory,
                    onValueChange = { kdfMemory = it },
                    onValueChangeFinished = { prefs.kdfMemoryPow2 = kdfMemory.roundToInt() },
                    valueRange = 12f..18f,
                    steps = 5
                )

                // Parallelism
                val paraVal = kdfParallelism.roundToInt()
                val paraLabel = if (paraVal == 4) DEFAULT_LABEL else ""
                Text("Parallelism: $paraVal Threads $paraLabel", fontWeight = FontWeight.Bold)
                Slider(
                    value = kdfParallelism,
                    onValueChange = { kdfParallelism = it },
                    onValueChangeFinished = { prefs.kdfParallelism = kdfParallelism.roundToInt() },
                    valueRange = 1f..8f,
                    steps = 6
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- CLIPBOARD & PRIVACY ---
        SettingsHeader("Privacy & Clipboard")

        SettingsItem(
            title = "Screen Shield",
            desc = "Block screenshots and hide content in Recents. (Requires Restart)",
            trailing = {
                Switch(checked = screenShield, onCheckedChange = {
                    screenShield = it
                    prefs.isScreenShieldEnabled = it
                })
            }
        )

        Spacer(Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Clipboard Auto-Wipe: ${clipTimeout.toInt()}s", fontWeight = FontWeight.Bold)
                Slider(
                    value = clipTimeout,
                    onValueChange = { clipTimeout = it },
                    onValueChangeFinished = { prefs.clipboardTimeoutSeconds = clipTimeout.toInt() },
                    valueRange = 5f..120f,
                    steps = 22
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- APP LOCK ---
        SettingsHeader("App Lock")

        SettingsItem(
            title = "Require Authentication",
            desc = "Lock app on startup/resume.",
            trailing = {
                Switch(
                    checked = lockMode != LockMode.NONE,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            if (viewModel.hasAppLockSet()) {
                                lockMode = LockMode.CUSTOM
                                viewModel.setLockMode(LockMode.CUSTOM)
                            } else {
                                pendingLockMode = LockMode.CUSTOM
                                isSetupFlowActive = true
                                setupStep = 0
                            }
                        } else {
                            lockMode = LockMode.NONE
                            viewModel.setLockMode(LockMode.NONE)
                        }
                    }
                )
            }
        )

        AnimatedVisibility(visible = lockMode != LockMode.NONE) {
            Column {
                Spacer(Modifier.height(12.dp))
                SigilSegmentedControl(
                    items = listOf("Device Biometrics", "Custom Lock"),
                    selectedIndex = if (lockMode == LockMode.CUSTOM) 1 else 0,
                    onItemSelection = { index ->
                        if (index == 0) {
                            if (!BiometricHelper.isDeviceSecure(context)) {
                                showSecurityErrorDialog = true
                            } else if (!viewModel.hasAppLockSet()) {
                                pendingLockMode = LockMode.DEVICE
                                isSetupFlowActive = true
                                setupStep = 0
                            } else {
                                lockMode = LockMode.DEVICE
                                viewModel.setLockMode(LockMode.DEVICE)
                            }
                        } else {
                            if (viewModel.hasAppLockSet()) {
                                lockMode = LockMode.CUSTOM
                                viewModel.setLockMode(LockMode.CUSTOM)
                            } else {
                                pendingLockMode = LockMode.CUSTOM
                                isSetupFlowActive = true
                                setupStep = 0
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { showVerifyPinDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Verify My Secret")
                }

                Spacer(Modifier.height(2.dp))

                TextButton(
                    onClick = {
                        pendingLockMode = lockMode
                        isSetupFlowActive = true
                        setupStep = 0
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Lock Secret")
                }

                Spacer(Modifier.height(8.dp))
            }
        }

        // Grace Period
        AnimatedVisibility(visible = lockMode != LockMode.NONE) {
            Column {
                SettingsItem(
                    title = "Keep Unlocked",
                    desc = "Don't lock immediately when switching apps.",
                    trailing = {
                        Switch(checked = graceEnabled, onCheckedChange = {
                            graceEnabled = it
                            prefs.isGracePeriodEnabled = it
                        })
                    }
                )

                AnimatedVisibility(visible = graceEnabled) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Text(
                            text = "Timeout: ${graceMinutes.toInt()} minutes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Slider(
                            value = graceMinutes,
                            onValueChange = { graceMinutes = it },
                            onValueChangeFinished = { prefs.graceDurationMinutes = graceMinutes.toInt() },
                            valueRange = 1f..60f,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // --- APPEARANCE ---
        SettingsHeader("Appearance")

        SettingsItem(
            title = "Material You",
            desc = if (supportsDynamicColor) "Use system wallpaper colors. (Requires Restart)"
            else "Not supported on this Android version.",
            trailing = {
                Switch(
                    checked = dynamicColors,
                    onCheckedChange = {
                        dynamicColors = it
                        viewModel.setDynamicColors(it)
                    },
                    enabled = supportsDynamicColor
                )
            }
        )

        // Accent Color
        AnimatedVisibility(visible = !dynamicColors) {
            Column {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .clickable { showColorDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Accent Color", fontWeight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${Integer.toHexString(selectedColorInt).uppercase().takeLast(6)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(selectedColorInt))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Dark Mode
        val isAppEffectivelyDark = if (dynamicColors) systemDark else darkMode

        SettingsItem(
            title = "Dark Mode",
            desc = if (dynamicColors) "Following system theme (Material You is enabled)."
            else "Force dark theme. (Requires Restart)",
            trailing = {
                Switch(
                    checked = isAppEffectivelyDark,
                    onCheckedChange = {
                        darkMode = it
                        viewModel.setDarkMode(it)
                    },
                    enabled = !dynamicColors
                )
            }
        )

        Spacer(Modifier.height(24.dp))

        // --- DATA & PROFILES ---
        SettingsHeader("Data Management")

        OutlinedButton(
            onClick = { showResetSettingsDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
        ) {
            Icon(Icons.Default.SettingsBackupRestore, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reset App Preferences")
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { showWipeDataDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Wipe All Data")
        }

        Spacer(Modifier.height(64.dp))
    }

    // --- DIALOGS ---

    if (showSecurityErrorDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityErrorDialog = false },
            icon = { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Device Security Not Set") },
            text = { Text("Your device doesn't have a Screen Lock (PIN, Pattern, or Password) enabled.\n\nSigil requires this to secure your Biometrics.") },
            confirmButton = {
                Button(onClick = { showSecurityErrorDialog = false }) { Text("OK") }
            }
        )
    }

    // --- LOCK SETUP FLOW  ---
    if (isSetupFlowActive) {
        val centerText = Modifier.fillMaxWidth()
        val centerAlign = androidx.compose.ui.text.style.TextAlign.Center

        when (setupStep) {
            0 -> { // STEP 0: TYPE SELECTION
                androidx.compose.ui.window.Dialog(onDismissRequest = { cleanupSetup(false) }) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Choose Lock Type",
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = centerAlign
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "How do you want to secure Sigil?",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = centerAlign
                            )

                            Spacer(Modifier.height(24.dp))

                            OutlinedButton(
                                onClick = {
                                    selectedLockType = LockType.PIN
                                    setupStep = 1
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Pin, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Numeric PIN")
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = {
                                    selectedLockType = LockType.PASSWORD
                                    setupStep = 1
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Password, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Alphanumeric Password")
                            }

                            Spacer(Modifier.height(12.dp))

                            TextButton(
                                onClick = { cleanupSetup(false) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
            1 -> { // STEP 1: CREATE INPUT
                var firstInput by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { cleanupSetup(false) },
                    icon = { Icon(Icons.Default.Lock, null) },
                    title = {
                        Text(
                            text = if (selectedLockType == LockType.PIN) "Create PIN" else "Create Password",
                            modifier = centerText,
                            textAlign = centerAlign
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Enter your new secret.",
                                textAlign = centerAlign,
                                modifier = centerText
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = firstInput,
                                onValueChange = { input ->
                                    firstInput = if (selectedLockType == LockType.PIN) input.filter { it.isDigit() } else input
                                },
                                label = { Text("Enter Secret") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (selectedLockType == LockType.PIN) KeyboardType.NumberPassword else KeyboardType.Password
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (firstInput.isNotEmpty()) {
                                    tempSecret = firstInput
                                    setupStep = 2
                                }
                            },
                            enabled = firstInput.isNotEmpty()
                        ) { Text("Next") }
                    },
                    dismissButton = { TextButton(onClick = { cleanupSetup(false) }) { Text("Cancel") } }
                )
            }
            2 -> { // STEP 2: CONFIRMATION
                var confirmInput by remember { mutableStateOf("") }
                var error by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { cleanupSetup(false) },
                    icon = { Icon(Icons.Default.VerifiedUser, null) },
                    title = {
                        Text(
                            text = "Confirm Secret",
                            modifier = centerText,
                            textAlign = centerAlign
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Re-enter to confirm.",
                                textAlign = centerAlign,
                                modifier = centerText
                            )
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = confirmInput,
                                onValueChange = { input ->
                                    confirmInput = if (selectedLockType == LockType.PIN) input.filter { it.isDigit() } else input
                                    error = false
                                },
                                label = { Text("Confirm Secret") },
                                singleLine = true,
                                isError = error,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (selectedLockType == LockType.PIN) KeyboardType.NumberPassword else KeyboardType.Password
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (error) {
                                Text(
                                    "Secrets do not match.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .fillMaxWidth(),
                                    textAlign = centerAlign
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (confirmInput == tempSecret) {
                                    isSavingPin = true
                                    viewModel.setAppLock(tempSecret, selectedLockType) { success ->
                                        if (success) {
                                            val newMode = pendingLockMode ?: LockMode.CUSTOM
                                            lockMode = newMode
                                            viewModel.setLockMode(newMode)
                                            cleanupSetup(true)
                                        } else {
                                            isSavingPin = false
                                        }
                                    }
                                } else {
                                    error = true
                                }
                            },
                            enabled = !isSavingPin
                        ) {
                            if (isSavingPin) CircularProgressIndicator(Modifier.size(16.dp)) else Text("Confirm & Save")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { cleanupSetup(false) }, enabled = !isSavingPin) { Text("Cancel") }
                    }
                )
            }
        }
    }

    if (showVerifyPinDialog) {
        var testInput by remember { mutableStateOf("") }
        var verificationResult by remember { mutableStateOf<Boolean?>(null) }
        var isVerifying by remember { mutableStateOf(false) }
        val inputType = prefs.lockType

        AlertDialog(
            onDismissRequest = { showVerifyPinDialog = false },
            icon = { Icon(Icons.Default.VerifiedUser, null) },
            title = { Text("Verify Secret") },
            text = {
                Column {
                    Text("Type your current secret to verify.")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = testInput,
                        onValueChange = {
                            testInput = it
                            verificationResult = null
                        },
                        label = { Text("Enter Secret") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (inputType == LockType.PIN) KeyboardType.NumberPassword else KeyboardType.Password
                        )
                    )

                    if (verificationResult != null) {
                        Spacer(Modifier.height(8.dp))
                        if (verificationResult == true) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Correct!", color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Text("Incorrect", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isVerifying = true
                        viewModel.verifyAppSecret(testInput) { isValid ->
                            isVerifying = false
                            verificationResult = isValid
                        }
                    },
                    enabled = !isVerifying && testInput.isNotEmpty()
                ) {
                    if (isVerifying) CircularProgressIndicator(Modifier.size(16.dp)) else Text("Check")
                }
            },
            dismissButton = { TextButton(onClick = { showVerifyPinDialog = false }) { Text("Close") } }
        )
    }

    if (showColorDialog) {
        AdvancedColorPickerDialog(
            initialColor = Color(selectedColorInt),
            onDismiss = { showColorDialog = false },
            onColorSelected = {
                selectedColorInt = it.toArgb()
                viewModel.setThemeColor(it)
                showColorDialog = false
            }
        )
    }

    // CONFIRM RESET SETTINGS
    if (showResetSettingsDialog) {
        var shouldRestart by remember { mutableStateOf(false) }
        var resetAll by remember { mutableStateOf(true) }
        var resetGeneral by remember { mutableStateOf(true) }
        var resetAppearance by remember { mutableStateOf(true) }
        var resetKdf by remember { mutableStateOf(true) }
        var resetProfiles by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showResetSettingsDialog = false },
            icon = { Icon(Icons.Default.SettingsBackupRestore, null) },
            title = { Text("Reset App Preferences") },
            text = {
                Column {
                    Text("Select which settings you want to restore to default values.")
                    Spacer(modifier = Modifier.height(16.dp))

                    // Reset All Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { resetAll = !resetAll },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = resetAll,
                            onCheckedChange = { resetAll = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Reset Everything", fontWeight = FontWeight.Bold)
                    }

                    AnimatedVisibility(visible = !resetAll) {
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            // Helper composable
                            @Composable
                            fun ResetOption(label: String, state: Boolean, onStateChange: (Boolean) -> Unit) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onStateChange(!state) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = state, onCheckedChange = onStateChange)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(label)
                                }
                            }

                            ResetOption("General, Privacy & App Lock", resetGeneral) { resetGeneral = it }
                            ResetOption("Appearance Settings", resetAppearance) { resetAppearance = it }
                            ResetOption("Global Encryption Defaults", resetKdf) { resetKdf = it }
                            ResetOption("Saved Encryption Profiles", resetProfiles) { resetProfiles = it }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { shouldRestart = !shouldRestart },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = shouldRestart,
                            onCheckedChange = { shouldRestart = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restart App immediately")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val finalGeneral = resetAll || resetGeneral
                    val finalAppearance = resetAll || resetAppearance
                    val finalKdf = resetAll || resetKdf
                    val finalProfiles = resetAll || resetProfiles

                    // Perform Reset via Prefs
                    prefs.resetSettings(
                        resetGeneral = finalGeneral,
                        resetAppearance = finalAppearance,
                        resetKdf = finalKdf,
                        resetProfiles = finalProfiles,
                        synchronous = shouldRestart
                    )

                    if (shouldRestart) {
                        restartApp(context)
                    } else {
                        // Manually update UI state
                        val freshPrefs = viewModel.getPrefs()

                        if (finalGeneral) {
                            onboardingToggle = true
                            lockMode = freshPrefs.lockMode
                            graceEnabled = freshPrefs.isGracePeriodEnabled
                            graceMinutes = freshPrefs.graceDurationMinutes.toFloat()
                            screenShield = freshPrefs.isScreenShieldEnabled
                            clipTimeout = freshPrefs.clipboardTimeoutSeconds.toFloat()
                        }

                        if (finalAppearance) {
                            dynamicColors = freshPrefs.isDynamicColorsEnabled
                            darkMode = freshPrefs.isDarkModeEnabled
                            selectedColorInt = freshPrefs.selectedThemeColor
                        }

                        if (finalKdf) {
                            kdfIterations = freshPrefs.kdfIterations.toFloat()
                            kdfMemory = freshPrefs.kdfMemoryPow2.toFloat()
                            kdfParallelism = freshPrefs.kdfParallelism.toFloat()
                        }
                    }

                    showResetSettingsDialog = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }

    // CONFIRM WIPE ALL
    if (showWipeDataDialog) {
        AlertDialog(
            onDismissRequest = { showWipeDataDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Scorched Earth") },
            text = { Text("This will permanently delete ALL data, including:\n- Keys in Vault\n- Settings & Profiles\n- PINs & Biometrics\n\nThe app will restart.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.wipeAllData() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("NUKE IT") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDataDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Displays a styled section header for settings screens.
 *
 * Renders the provided text using the theme's `labelLarge` typography and primary color,
 * with leading and bottom padding to align with other settings content.
 *
 * @param text The header text to display.
 */
@Composable
fun SettingsHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsItem(title: String, desc: String, trailing: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(desc) },
        trailingContent = trailing,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    )
}

@Composable
fun RowScope.RGBField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 3) onValueChange(it) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )
}

/**
 * Shows a dialog that lets the user pick an accent color using an HSV color wheel, a brightness slider,
 * a 6-digit HEX input, or individual R/G/B inputs.
 *
 * The dialog initializes to `initialColor`. User interactions update an internal selected color;
 * tapping "Apply" invokes `onColorSelected` with the current selection, and dismissing the dialog
 * invokes `onDismiss`.
 *
 * @param initialColor The starting color shown when the dialog opens.
 * @param onDismiss Called when the dialog is dismissed or the "Cancel" button is pressed.
 * @param onColorSelected Called with the selected Color when the user confirms ("Apply").
 */
@Composable
fun AdvancedColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var currentColor by remember { mutableStateOf(initialColor) }

    // HSV State
    val hsv = remember {
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), arr)
        mutableStateOf(arr)
    }

    // Inputs
    var hexInput by remember { mutableStateOf(Integer.toHexString(initialColor.toArgb()).uppercase().takeLast(6)) }
    var rInput by remember { mutableStateOf((initialColor.red * 255).toInt().toString()) }
    var gInput by remember { mutableStateOf((initialColor.green * 255).toInt().toString()) }
    var bInput by remember { mutableStateOf((initialColor.blue * 255).toInt().toString()) }

    fun updateInputs(color: Color) {
        currentColor = color
        hexInput = Integer.toHexString(color.toArgb()).uppercase().takeLast(6)
        rInput = (color.red * 255).toInt().toString()
        gInput = (color.green * 255).toInt().toString()
        bInput = (color.blue * 255).toInt().toString()
    }

    fun updateFromHex(hex: String) {
        try {
            if (hex.length == 6) {
                val color = Color("#$hex".toColorInt())
                currentColor = color
                android.graphics.Color.colorToHSV(color.toArgb(), hsv.value)
                updateInputs(color)
            }
        } catch (_: Exception) {
            // Ignore invalid hex input while typing
        }
    }

    fun updateFromRGB(r: String, g: String, b: String) {
        // Safe parsing with defaults; no try-catch needed
        val ri = r.toIntOrNull() ?: 0
        val gi = g.toIntOrNull() ?: 0
        val bi = b.toIntOrNull() ?: 0

        val color = Color(ri.coerceIn(0, 255), gi.coerceIn(0, 255), bi.coerceIn(0, 255))
        currentColor = color
        hexInput = Integer.toHexString(color.toArgb()).uppercase().takeLast(6)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv.value)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Accent Color") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. CIRCULAR WHEEL
                @Suppress("COMPOSE_APPLIER_CALL_MISMATCH")
                BoxWithConstraints(
                    modifier = Modifier.size(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val density = LocalDensity.current
                    val maxWidthPx = with(density) { maxWidth.toPx() }
                    val radiusPx = maxWidthPx / 2f

                    val angleRad = Math.toRadians(hsv.value[0].toDouble())
                    val dist = hsv.value[1] * (maxWidth.value / 2f)
                    val thumbX = (cos(angleRad) * dist).dp
                    val thumbY = (sin(angleRad) * dist).dp

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val delta = change.position - center
                                    val angle = (atan2(delta.y, delta.x) * (180 / Math.PI) + 360) % 360
                                    val distance = hypot(delta.x, delta.y)
                                    val saturation = (distance / radiusPx).coerceIn(0f, 1f)

                                    hsv.value[0] = angle.toFloat()
                                    hsv.value[1] = saturation
                                    val newColor = Color.hsv(hsv.value[0], hsv.value[1], hsv.value[2])
                                    updateInputs(newColor)
                                }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val delta = offset - center
                                    val angle = (atan2(delta.y, delta.x) * (180 / Math.PI) + 360) % 360
                                    val distance = hypot(delta.x, delta.y)
                                    val saturation = (distance / radiusPx).coerceIn(0f, 1f)

                                    hsv.value[0] = angle.toFloat()
                                    hsv.value[1] = saturation
                                    val newColor = Color.hsv(hsv.value[0], hsv.value[1], hsv.value[2])
                                    updateInputs(newColor)
                                }
                            }
                    ) {
                        drawCircle(brush = Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                        drawCircle(brush = Brush.radialGradient(listOf(Color.White, Color.Transparent)))
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = thumbX, y = thumbY)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, Color.White, CircleShape)
                            .border(1.dp, Color.Black, CircleShape)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 2. BRIGHTNESS SLIDER
                Text("Brightness: ${(hsv.value[2] * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = hsv.value[2],
                    onValueChange = {
                        hsv.value[2] = it
                        val newColor = Color.hsv(hsv.value[0], hsv.value[1], hsv.value[2])
                        updateInputs(newColor)
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // 3. HEX INPUT
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = {
                        if (it.length <= 6) {
                            hexInput = it.uppercase()
                            updateFromHex(it)
                        }
                    },
                    label = { Text("HEX") },
                    prefix = { Text("#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(0.6f),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(16.dp))

                // 4. RGB INPUTS
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RGBField("R", rInput) { rInput = it; updateFromRGB(it, gInput, bInput) }
                    RGBField("G", gInput) { gInput = it; updateFromRGB(rInput, it, bInput) }
                    RGBField("B", bInput) { bInput = it; updateFromRGB(rInput, gInput, it) }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(currentColor) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun restartApp(context: Context) {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(context.packageName)
    val componentName = intent?.component
    val mainIntent = Intent.makeRestartActivityTask(componentName)
    context.startActivity(mainIntent)
    exitProcess(0)
}