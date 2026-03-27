package dev.animeshvarma.sigil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.SigilMode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

enum class OnboardingState {
    START_SCREEN,
    BASIC_INTRO,
    BASIC_PROFILE_EXPLAIN,
    BASIC_INPUT, BASIC_PASS, BASIC_ENCRYPT_WAIT, BASIC_ENCRYPT_DONE, BASIC_OUTPUT,
    DECRYPT_PREP, DECRYPT_WAIT, DECRYPT_DONE,
    DRAWER_SHOW,
    KEYSTORE_NAV, KEYSTORE_EXPLAIN, KEYSTORE_USAGE,
    SETTINGS_NAV, SETTINGS_EXPLAIN,
    FORK_SELECTION,
    ADV_CUSTOM_INTRO, ADV_CUSTOM_LAYERS, ADV_CUSTOM_REORDER, ADV_BLOB_EXPLAIN,
    ADV_LOGS_PREP, ADV_LOGS_VIEW, ADV_RELEASES,
    FINISHED
}

private const val DEMO_LOREM = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Pellentesque semper, nisi ac cursus vulputate, diam est cursus nibh, non suscipit lorem libero vitae metus. Donec pharetra lectus id erat aliquet, eu volutpat felis condimentum."

private data class StepConfig(
    val title: String,
    val body: String,
    val next: OnboardingState = OnboardingState.FINISHED,
    val alignment: Alignment = Alignment.BottomCenter,
    val btnLabel: String = "Next"
)

private val OnboardingState.config: StepConfig
    get() = when (this) {
        OnboardingState.BASIC_INTRO -> StepConfig(
            "Primary Workspace",
            "This is the Auto tab, which is divided into three primary components: Input, Password, and Output.",
            next = OnboardingState.BASIC_PROFILE_EXPLAIN
        )
        OnboardingState.BASIC_PROFILE_EXPLAIN -> StepConfig(
            "Security Profile",
            "Use the bookmark icon to switch Encryption Profiles.\n\n• Sigil Chain: Maximum security (Cascaded).\n• Standard AES: Maximum compatibility (Raw Mode).\n\nYou can create your own chains in the Custom tab.",
            next = OnboardingState.BASIC_INPUT
        )
        OnboardingState.BASIC_INPUT -> StepConfig(
            "1. Input",
            "Enter your plain text or message into the first field for processing.",
            next = OnboardingState.BASIC_PASS
        )
        OnboardingState.BASIC_PASS -> StepConfig(
            "2. Password",
            "Secure your message with a strong password here. You can use the visibility icon to verify your input before proceeding.",
            next = OnboardingState.BASIC_ENCRYPT_WAIT
        )
        OnboardingState.BASIC_ENCRYPT_WAIT -> StepConfig(
            "3. Execution",
            "Tapping Encrypt applies the active profile's algorithm chain (e.g., Quad-Layer Cascade).",
            next = OnboardingState.BASIC_ENCRYPT_DONE,
            alignment = Alignment.TopCenter,
            btnLabel = "Encrypt"
        )
        OnboardingState.BASIC_ENCRYPT_DONE -> StepConfig(
            "Processing...",
            "The system is processing...\n\nFun Fact: The majority of execution time is consumed by Key Derivation (KDF), not the encryption algorithms themselves.",
            next = OnboardingState.BASIC_OUTPUT,
            alignment = Alignment.TopCenter
        )
        OnboardingState.BASIC_OUTPUT -> StepConfig(
            "4. Ciphertext",
            "The resulting output is impossible to break and requires the specific key for decryption.",
            next = OnboardingState.DECRYPT_PREP,
            alignment = Alignment.TopCenter
        )
        OnboardingState.DECRYPT_PREP -> StepConfig(
            "5. Decryption",
            "To decrypt a message, paste the output directly back into the Input field on this or another device.",
            next = OnboardingState.DECRYPT_WAIT
        )
        OnboardingState.DECRYPT_WAIT -> StepConfig(
            "6. Authenticate",
            "Enter the correct password to authenticate and reveal the original message.",
            next = OnboardingState.DECRYPT_DONE,
            btnLabel = "Decrypt"
        )
        OnboardingState.DECRYPT_DONE -> StepConfig(
            "7. Verification",
            "Decryption was successful and the original plain text has been restored.",
            next = OnboardingState.DRAWER_SHOW,
            alignment = Alignment.TopCenter
        )
        OnboardingState.DRAWER_SHOW -> StepConfig(
            "Navigation",
            "You can access additional tools and settings by opening the navigation drawer from the left edge or by tapping the ☰ icon.",
            next = OnboardingState.KEYSTORE_NAV
        )
        OnboardingState.KEYSTORE_NAV -> StepConfig(
            "Keystore Module",
            "The Key Store manages your saved credentials.",
            next = OnboardingState.KEYSTORE_EXPLAIN
        )
        OnboardingState.KEYSTORE_EXPLAIN -> StepConfig(
            "Hardware Security",
            "Keys are stored within the device's hardware-backed Trusted Execution Environment.\n\nFor safety, never transmit the key and the message on the same channel. In-person exchange is the most secure method.",
            next = OnboardingState.KEYSTORE_USAGE
        )
        OnboardingState.KEYSTORE_USAGE -> StepConfig(
            "Rapid Access",
            "You can retrieve these securely saved keys by tapping the key icon inside any password field.",
            next = OnboardingState.SETTINGS_NAV
        )
        OnboardingState.SETTINGS_NAV -> StepConfig(
            "Settings",
            "The Settings panel controls application behavior and security features.",
            next = OnboardingState.SETTINGS_EXPLAIN
        )
        OnboardingState.SETTINGS_EXPLAIN -> StepConfig(
            "Control Panel",
            "Configure App Lock, Grace Periods, Encryption parameters, and Screen Shield here.\n\nDisabling Material You allows for manual color customization.",
            next = OnboardingState.FORK_SELECTION
        )
        OnboardingState.ADV_CUSTOM_INTRO -> StepConfig(
            "Custom Workbench",
            "This interface allows you to construct unique encryption chains.",
            next = OnboardingState.ADV_CUSTOM_LAYERS
        )
        OnboardingState.ADV_CUSTOM_LAYERS -> StepConfig(
            "Algorithm Registry",
            "Select from over 15 industrial-grade algorithms, including AES, Twofish, Camellia, and GOST.",
            next = OnboardingState.ADV_CUSTOM_REORDER
        )
        OnboardingState.ADV_CUSTOM_REORDER -> StepConfig(
            "Sequence Control",
            "Use the arrow controls to reorder the execution sequence, or add new layers using the add button.",
            next = OnboardingState.ADV_BLOB_EXPLAIN
        )
        OnboardingState.ADV_BLOB_EXPLAIN -> StepConfig(
            "Container Anatomy",
            "The output structure combines the Header, Salt, IVs, Ciphertext, and HMAC. Auto Mode parses the header to determine the decryption sequence automatically.",
            next = OnboardingState.ADV_LOGS_PREP,
            alignment = Alignment.TopCenter
        )
        OnboardingState.ADV_LOGS_PREP -> StepConfig(
            "System Console",
            "The system console allows you to audit every encryption step, timing metric, and key derivation in real-time.",
            next = OnboardingState.ADV_LOGS_VIEW,
            btnLabel = "Open Logs"
        )
        OnboardingState.ADV_LOGS_VIEW -> StepConfig(
            "Audit Log",
            "This view displays the raw telemetry data from the cryptographic operation you just performed.",
            next = OnboardingState.ADV_RELEASES
        )
        OnboardingState.ADV_RELEASES -> StepConfig(
            "Transparency",
            "Review major application updates here, or check the repository for detailed release notes.",
            next = OnboardingState.FINISHED,
            btnLabel = "Finish"
        )
        OnboardingState.START_SCREEN,
        OnboardingState.FORK_SELECTION,
        OnboardingState.FINISHED -> error("$this has no StepConfig; handled separately in OnboardingOrchestrator")
    }

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
            OnboardingState.DECRYPT_PREP -> {
                val output = viewModel.uiState.value.autoOutput
                viewModel.injectDemoData(output, "BlueHorse", "")
            }
            OnboardingState.DRAWER_SHOW -> {
                viewModel.onScreenSelected(AppScreen.HOME)
                delay(300)
                viewModel.toggleDemoDrawer(true)
            }
            OnboardingState.KEYSTORE_NAV -> {
                viewModel.toggleDemoDrawer(false)
                delay(400)
                viewModel.injectDemoVault()
                viewModel.onScreenSelected(AppScreen.KEYSTORE)
            }
            OnboardingState.KEYSTORE_USAGE -> {
                viewModel.onScreenSelected(AppScreen.HOME)
                delay(500)
                viewModel.toggleDemoDropdown(true)
            }
            OnboardingState.SETTINGS_NAV -> {
                viewModel.toggleDemoDropdown(false)
                viewModel.onScreenSelected(AppScreen.SETTINGS)
            }
            OnboardingState.FORK_SELECTION -> viewModel.onScreenSelected(AppScreen.HOME)
            OnboardingState.ADV_CUSTOM_INTRO -> {
                viewModel.onScreenSelected(AppScreen.HOME)
                viewModel.onModeSelected(SigilMode.CUSTOM)
                viewModel.injectDemoData("Launch Codes", "RedBattery", "")
            }
            OnboardingState.ADV_CUSTOM_REORDER -> {
                delay(500)
                viewModel.demoSwapLayers()
            }
            OnboardingState.ADV_LOGS_PREP -> { if (viewModel.uiState.value.showLogsDialog) viewModel.onLogsClicked() }
            OnboardingState.ADV_LOGS_VIEW -> { if (!viewModel.uiState.value.showLogsDialog) viewModel.onLogsClicked() }
            OnboardingState.ADV_RELEASES -> {
                if (viewModel.uiState.value.showLogsDialog) viewModel.onLogsClicked()
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
        when (state) {
            OnboardingState.START_SCREEN -> StartScreen(
                onStart = { state = OnboardingState.BASIC_INTRO },
                onSkip = { state = OnboardingState.FINISHED }
            )
            OnboardingState.FORK_SELECTION -> ForkSelectionScreen(
                onFinish = { state = OnboardingState.FINISHED },
                onAdvanced = { state = OnboardingState.ADV_CUSTOM_INTRO }
            )
            OnboardingState.FINISHED -> Box(Modifier.fillMaxSize())
            else -> {
                Box(modifier = Modifier.fillMaxSize().clickable(enabled = true, onClick = {}))

                PromptOverlay(state = state) {
                    when (state) {
                        OnboardingState.BASIC_ENCRYPT_WAIT -> viewModel.onEncrypt()
                        OnboardingState.DECRYPT_WAIT -> viewModel.onDecrypt()
                        OnboardingState.DRAWER_SHOW -> viewModel.toggleDemoDrawer(false)
                        else -> {}
                    }
                    state = state.config.next
                }
            }
        }
    }
}

@Composable
private fun StartScreen(
    onStart: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Security, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(32.dp))
            Text("SIGIL", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("A Zero-Trust Encryption Environment", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(64.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Start Tour")
            }
            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onSkip) {
                Text("Skip Intro", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PromptOverlay(state: OnboardingState, onNext: () -> Unit) {
    val config = state.config

    var offsetX by remember(state) { mutableFloatStateOf(0f) }
    var offsetY by remember(state) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.05f))
            .padding(24.dp),
        contentAlignment = config.alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(state) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = config.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))

                Text(
                    text = config.body,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onNext) {
                        Text(config.btnLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ForkSelectionScreen(onFinish: () -> Unit, onAdvanced: () -> Unit) {
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
            "You know the essentials.\n\n(Note: The Profiles tab onboarding will be added in v0.5.0)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
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