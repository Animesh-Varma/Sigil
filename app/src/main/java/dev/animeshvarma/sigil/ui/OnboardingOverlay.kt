package dev.animeshvarma.sigil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.SigilMode
import kotlinx.coroutines.delay

enum class OnboardingState {
    START_SCREEN,
    BASIC_INTRO, BASIC_INPUT, BASIC_PASS, BASIC_ENCRYPT_WAIT, BASIC_ENCRYPT_DONE, BASIC_OUTPUT,
    DECRYPT_PREP, DECRYPT_WAIT, DECRYPT_DONE,
    KEYSTORE_NAV, KEYSTORE_EXPLAIN, KEYSTORE_USAGE,
    FORK_SELECTION,
    ADV_CUSTOM_INTRO, ADV_CUSTOM_LAYERS, ADV_CUSTOM_REORDER,
    ADV_BLOB_EXPLAIN,
    ADV_LOGS,
    ADV_RELEASES,
    FINISHED
}

// Demo Text Constant
private const val DEMO_LOREM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Pellentesque semper, nisi ac cursus vulputate, diam est cursus nibh, non suscipit lorem libero vitae metus. Donec pharetra lectus id erat aliquet, eu volutpat felis condimentum."

@Composable
fun OnboardingOrchestrator(
    viewModel: SigilViewModel,
    onComplete: () -> Unit
) {
    var state by remember { mutableStateOf(OnboardingState.START_SCREEN) }

    LaunchedEffect(state) {
        delay(100)

        when (state) {
            OnboardingState.START_SCREEN -> { /* Static */ }

            OnboardingState.BASIC_INTRO -> {
                viewModel.setDemoMode(true)
                viewModel.onScreenSelected(AppScreen.HOME)
                viewModel.onModeSelected(SigilMode.AUTO)
                viewModel.injectDemoData("", "", "")
            }
            OnboardingState.BASIC_INPUT -> viewModel.injectDemoData(DEMO_LOREM, "", "")
            OnboardingState.BASIC_PASS -> viewModel.injectDemoData(DEMO_LOREM, "BlueHorse", "")

            OnboardingState.BASIC_ENCRYPT_WAIT -> { /* Wait */ }
            OnboardingState.BASIC_ENCRYPT_DONE -> {
                viewModel.onEncrypt()
            }

            OnboardingState.DECRYPT_PREP -> {
                val output = viewModel.uiState.value.autoOutput
                viewModel.injectDemoData(output, "BlueHorse", "")
            }
            OnboardingState.DECRYPT_WAIT -> { /* Wait */ }
            OnboardingState.DECRYPT_DONE -> {
                viewModel.onDecrypt()
            }

            OnboardingState.KEYSTORE_NAV -> {
                viewModel.injectDemoVault()
                viewModel.onScreenSelected(AppScreen.KEYSTORE)
            }
            OnboardingState.KEYSTORE_USAGE -> {
                viewModel.onScreenSelected(AppScreen.HOME)
                delay(500)
                viewModel.toggleDemoDropdown(true)
            }

            OnboardingState.ADV_CUSTOM_INTRO -> {
                viewModel.toggleDemoDropdown(false)
                viewModel.onScreenSelected(AppScreen.HOME)
                viewModel.onModeSelected(SigilMode.CUSTOM)
                viewModel.injectDemoData("Launch Codes", "RedBattery", "")
            }
            OnboardingState.ADV_CUSTOM_REORDER -> {
                delay(500)
                viewModel.demoSwapLayers()
            }

            // Logs Step
            OnboardingState.ADV_LOGS -> {
                if (!viewModel.uiState.value.showLogsDialog) {
                    viewModel.onLogsClicked()
                }
            }

            OnboardingState.ADV_RELEASES -> {
                if (viewModel.uiState.value.showLogsDialog) {
                    viewModel.onLogsClicked()
                }
                viewModel.onScreenSelected(AppScreen.DOCS)
                viewModel.setDocsTab(1)
            }

            OnboardingState.FINISHED -> {
                viewModel.setDemoMode(false)
                onComplete()
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        if (state == OnboardingState.START_SCREEN) {
            Box(
                Modifier.fillMaxSize().background(Color.Black).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Security, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(32.dp))
                    Text("SIGIL v0.3", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    Text("Zero-Trust Encryption Environment", color = Color.Gray)
                    Spacer(Modifier.height(64.dp))
                    Button(
                        onClick = { state = OnboardingState.BASIC_INTRO },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Start Tour")
                    }
                }
            }
        }
        else if (state == OnboardingState.FORK_SELECTION) {
            ForkSelectionScreen(
                onFinish = { state = OnboardingState.FINISHED },
                onAdvanced = { state = OnboardingState.ADV_CUSTOM_INTRO }
            )
        }
        else {
            Box(modifier = Modifier.fillMaxSize().clickable(enabled = true, onClick = {}))

            PromptOverlay(state = state) {
                state = when (state) {
                    OnboardingState.BASIC_INTRO -> OnboardingState.BASIC_INPUT
                    OnboardingState.BASIC_INPUT -> OnboardingState.BASIC_PASS
                    OnboardingState.BASIC_PASS -> OnboardingState.BASIC_ENCRYPT_WAIT
                    OnboardingState.BASIC_ENCRYPT_WAIT -> OnboardingState.BASIC_ENCRYPT_DONE
                    OnboardingState.BASIC_ENCRYPT_DONE -> OnboardingState.BASIC_OUTPUT
                    OnboardingState.BASIC_OUTPUT -> OnboardingState.DECRYPT_PREP

                    OnboardingState.DECRYPT_PREP -> OnboardingState.DECRYPT_WAIT
                    OnboardingState.DECRYPT_WAIT -> OnboardingState.DECRYPT_DONE
                    OnboardingState.DECRYPT_DONE -> OnboardingState.KEYSTORE_NAV

                    OnboardingState.KEYSTORE_NAV -> OnboardingState.KEYSTORE_EXPLAIN
                    OnboardingState.KEYSTORE_EXPLAIN -> OnboardingState.KEYSTORE_USAGE
                    OnboardingState.KEYSTORE_USAGE -> OnboardingState.FORK_SELECTION

                    OnboardingState.ADV_CUSTOM_INTRO -> OnboardingState.ADV_CUSTOM_LAYERS
                    OnboardingState.ADV_CUSTOM_LAYERS -> OnboardingState.ADV_CUSTOM_REORDER
                    OnboardingState.ADV_CUSTOM_REORDER -> OnboardingState.ADV_BLOB_EXPLAIN

                    OnboardingState.ADV_BLOB_EXPLAIN -> OnboardingState.ADV_LOGS
                    OnboardingState.ADV_LOGS -> OnboardingState.ADV_RELEASES

                    OnboardingState.ADV_RELEASES -> OnboardingState.FINISHED
                    else -> OnboardingState.FINISHED
                }
            }
        }
    }
}

@Composable
fun PromptOverlay(state: OnboardingState, onNext: () -> Unit) {
    val alignment = when(state) {
        OnboardingState.BASIC_INTRO,
        OnboardingState.BASIC_INPUT,
        OnboardingState.BASIC_PASS,
        OnboardingState.DECRYPT_PREP,
        OnboardingState.DECRYPT_WAIT,
            // Keystore & Advanced Bottom
        OnboardingState.KEYSTORE_NAV,
        OnboardingState.KEYSTORE_EXPLAIN,
        OnboardingState.KEYSTORE_USAGE,
        OnboardingState.ADV_CUSTOM_INTRO,
        OnboardingState.ADV_CUSTOM_LAYERS,
        OnboardingState.ADV_CUSTOM_REORDER,
            // Logs & Releases Bottom
        OnboardingState.ADV_LOGS,
        OnboardingState.ADV_RELEASES -> Alignment.BottomCenter

        // Top Group
        OnboardingState.BASIC_ENCRYPT_WAIT,
        OnboardingState.BASIC_ENCRYPT_DONE,
        OnboardingState.BASIC_OUTPUT,
        OnboardingState.DECRYPT_DONE,
        OnboardingState.ADV_BLOB_EXPLAIN -> Alignment.TopCenter

        else -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = getPromptTitle(state),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text(
                    text = getPromptBody(state),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (state == OnboardingState.BASIC_PASS) {
                    }

                    Button(onClick = onNext) {
                        val label = when(state) {
                            OnboardingState.BASIC_ENCRYPT_WAIT -> "Encrypt"
                            OnboardingState.DECRYPT_WAIT -> "Decrypt"
                            OnboardingState.ADV_RELEASES -> "Finish"
                            else -> "Next"
                        }
                        Text(label)
                    }
                }
            }
        }
    }
}

private fun getPromptTitle(state: OnboardingState): String = when(state) {
    OnboardingState.BASIC_INTRO -> "The Home Screen"
    OnboardingState.BASIC_INPUT -> "1. Input"
    OnboardingState.BASIC_PASS -> "2. Password"
    OnboardingState.BASIC_ENCRYPT_WAIT -> "3. Secure It"
    OnboardingState.BASIC_ENCRYPT_DONE -> "Processing..."
    OnboardingState.BASIC_OUTPUT -> "4. Very Secure"
    OnboardingState.DECRYPT_PREP -> "5. Decryption"
    OnboardingState.DECRYPT_WAIT -> "6. Unlock"
    OnboardingState.DECRYPT_DONE -> "7. Read Message"
    OnboardingState.KEYSTORE_NAV -> "Key Store"
    OnboardingState.KEYSTORE_EXPLAIN -> "TEE Security"
    OnboardingState.KEYSTORE_USAGE -> "Easy Access"
    OnboardingState.ADV_CUSTOM_INTRO -> "Custom Workbench"
    OnboardingState.ADV_CUSTOM_LAYERS -> "The Algorithms"
    OnboardingState.ADV_CUSTOM_REORDER -> "Total Control"
    OnboardingState.ADV_BLOB_EXPLAIN -> "Anatomy of a Blob"
    OnboardingState.ADV_LOGS -> "System Console"
    OnboardingState.ADV_RELEASES -> "Transparency"
    else -> ""
}

private fun getPromptBody(state: OnboardingState): String = when(state) {
    OnboardingState.BASIC_INTRO -> "This is your workspace. Sigil keeps it simple: Input, Password, Output."
    OnboardingState.BASIC_INPUT -> "Type your message here."
    OnboardingState.BASIC_PASS -> "Set a password.\n(Demo Password: BlueHorse)\n\nTip: You can verify it by looking at the text field."
    OnboardingState.BASIC_ENCRYPT_WAIT -> "Tap Encrypt. Sigil will randomize 3 layers of encryption (AES+Twofish+Serpent)."
    OnboardingState.BASIC_ENCRYPT_DONE -> "Calculating..."
    OnboardingState.BASIC_OUTPUT -> "This text is now LOCKED. No one, not even the government can read it without the key you just set."
    OnboardingState.DECRYPT_PREP -> "To read a message, paste the Encrypted Text into the Input box."
    OnboardingState.DECRYPT_WAIT -> "Enter the password and tap Decrypt."
    OnboardingState.DECRYPT_DONE -> "The message is revealed! The math guarantees only the password holder can see this."

    OnboardingState.KEYSTORE_NAV -> "This is the Key Store (Sidebar -> Key Store)."
    OnboardingState.KEYSTORE_EXPLAIN -> "Keys here are saved in your phone's physical security chip (TEE). It's safer than your brain."
    OnboardingState.KEYSTORE_USAGE -> "You can access these saved keys anytime by tapping the 🔑 icon inside any password field."

    OnboardingState.ADV_CUSTOM_INTRO -> "Welcome! This is where you build your own chains."
    OnboardingState.ADV_CUSTOM_LAYERS -> "Select from 15+ industrial algorithms (AES, Twofish, Camellia, GOST, etc)."
    OnboardingState.ADV_CUSTOM_REORDER -> "You can drag and reorder them. Watch Layer 1 and 2 swap positions..."
    OnboardingState.ADV_BLOB_EXPLAIN -> "How it works:\n\n[Header: Algo Order] + [Salt] + [IVs] + [Encrypted Data] + [HMAC Signature]\n\nAuto Mode reads the Header to know how to decrypt your Custom chains automatically."

    OnboardingState.ADV_LOGS -> "The 'Logs' button opens the real-time system console. You can audit every encryption step, timing metric, and algorithm choice here."
    OnboardingState.ADV_RELEASES -> "We hide nothing. This tab shows every code change, audit, and security patch."
    else -> ""
}

@Composable
fun ForkSelectionScreen(onFinish: () -> Unit, onAdvanced: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))
        Text("Basics Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(
            "You know the essentials.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))

        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Finish & Start App")
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, null)
                Spacer(Modifier.width(16.dp))
                Text("Show Advanced Details")
            }
        }
    }
}