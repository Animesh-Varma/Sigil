package dev.animeshvarma.sigil.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.SigilMode

enum class OnboardingState {
    FORK_SELECTION,
    // Basic Path
    BASIC_INTRO, BASIC_INPUT, BASIC_PASS, BASIC_ENCRYPT, BASIC_OUTPUT, BASIC_ADVICE,
    // Advanced Path
    ADV_CUSTOM, ADV_KEYSTORE, ADV_RELEASES,
    FINISHED
}

@Composable
fun OnboardingOrchestrator(
    viewModel: SigilViewModel,
    onComplete: () -> Unit
) {
    var state by remember { mutableStateOf(OnboardingState.FORK_SELECTION) }

    // --- THE PUPPET MASTER (Drives the Real App underneath) ---
    LaunchedEffect(state) {
        when (state) {
            OnboardingState.FORK_SELECTION -> {
                viewModel.setDemoMode(true)
                // Reset to neutral state
                viewModel.onScreenSelected(AppScreen.HOME)
            }
            // --- BASIC ---
            OnboardingState.BASIC_INTRO -> {
                viewModel.onScreenSelected(AppScreen.HOME)
                viewModel.onModeSelected(SigilMode.AUTO)
                viewModel.injectDemoData("", "")
            }
            OnboardingState.BASIC_INPUT -> viewModel.injectDemoData("Meeting at 9 PM", "")
            OnboardingState.BASIC_PASS -> viewModel.injectDemoData("Meeting at 9 PM", "BlueHorse")
            OnboardingState.BASIC_ENCRYPT -> viewModel.injectDemoData("Meeting at 9 PM", "BlueHorse")
            OnboardingState.BASIC_OUTPUT -> viewModel.injectDemoData("Meeting at 9 PM", "BlueHorse", "SIGIL_V3::[Encrypted Blob...]")

            // --- ADVANCED ---
            OnboardingState.ADV_CUSTOM -> {
                viewModel.onScreenSelected(AppScreen.HOME)
                viewModel.onModeSelected(SigilMode.CUSTOM)
                viewModel.injectDemoData("Launch Codes", "")
            }
            OnboardingState.ADV_KEYSTORE -> {
                viewModel.injectDemoVault() // Load fake keys into real VM
                viewModel.onScreenSelected(AppScreen.KEYSTORE)
            }
            OnboardingState.ADV_RELEASES -> viewModel.onScreenSelected(AppScreen.DOCS)

            OnboardingState.FINISHED -> {
                viewModel.setDemoMode(false)
                onComplete()
            }
            else -> {}
        }
    }

    // --- THE OVERLAY UI ---
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. FORK SCREEN (Opaque)
        if (state == OnboardingState.FORK_SELECTION) {
            ForkSelectionScreen(
                onBasic = { state = OnboardingState.BASIC_INTRO },
                onAdvanced = { state = OnboardingState.ADV_CUSTOM }
            )
        }
        // 2. TRANSPARENT OVERLAY (Pass-through visuals, intercept clicks)
        else {
            // Invisible scrim to block direct interaction with the app during tour
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(enabled = true, onClick = {}) // Consume clicks
            )

            // The Prompt Card
            PromptOverlay(state = state) {
                // Next Logic
                state = when (state) {
                    OnboardingState.BASIC_INTRO -> OnboardingState.BASIC_INPUT
                    OnboardingState.BASIC_INPUT -> OnboardingState.BASIC_PASS
                    OnboardingState.BASIC_PASS -> OnboardingState.BASIC_ENCRYPT
                    OnboardingState.BASIC_ENCRYPT -> OnboardingState.BASIC_OUTPUT
                    OnboardingState.BASIC_OUTPUT -> OnboardingState.BASIC_ADVICE
                    OnboardingState.BASIC_ADVICE -> OnboardingState.FINISHED

                    OnboardingState.ADV_CUSTOM -> OnboardingState.ADV_KEYSTORE
                    OnboardingState.ADV_KEYSTORE -> OnboardingState.ADV_RELEASES
                    OnboardingState.ADV_RELEASES -> OnboardingState.FINISHED
                    else -> OnboardingState.FINISHED
                }
            }
        }
    }
}

@Composable
fun PromptOverlay(state: OnboardingState, onNext: () -> Unit) {
    // Intelligent Positioning to avoid covering the active UI element
    val alignment = when(state) {
        // Input fields are at top -> Prompt at Bottom
        OnboardingState.BASIC_INTRO,
        OnboardingState.BASIC_INPUT,
        OnboardingState.BASIC_PASS,
        OnboardingState.ADV_CUSTOM -> Alignment.BottomCenter

        // Output field is at bottom -> Prompt at Top
        OnboardingState.BASIC_ENCRYPT,
        OnboardingState.BASIC_OUTPUT,
        OnboardingState.ADV_KEYSTORE, // List fills screen, Top is safer
        OnboardingState.ADV_RELEASES -> Alignment.TopCenter

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
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(20.dp))
                Button(onClick = onNext, modifier = Modifier.align(Alignment.End)) {
                    Text(if (state == OnboardingState.BASIC_ADVICE || state == OnboardingState.ADV_RELEASES) "Finish" else "Next")
                }
            }
        }
    }
}

// --- CONTENT STRINGS ---

private fun getPromptTitle(state: OnboardingState): String = when(state) {
    OnboardingState.BASIC_INTRO -> "The Home Screen"
    OnboardingState.BASIC_INPUT -> "1. Input"
    OnboardingState.BASIC_PASS -> "2. Password"
    OnboardingState.BASIC_ENCRYPT -> "3. Encrypt"
    OnboardingState.BASIC_OUTPUT -> "4. Output"
    OnboardingState.BASIC_ADVICE -> "Important Safety Tip"
    OnboardingState.ADV_CUSTOM -> "Custom Workbench"
    OnboardingState.ADV_KEYSTORE -> "Hardware Keystore"
    OnboardingState.ADV_RELEASES -> "Transparency"
    else -> ""
}

private fun getPromptBody(state: OnboardingState): String = when(state) {
    OnboardingState.BASIC_INTRO -> "This is your main workspace. Sigil keeps it simple: Input, Password, Output."
    OnboardingState.BASIC_INPUT -> "Type your message here."
    OnboardingState.BASIC_PASS -> "Set a password. Or, tap the 🔑 icon to use a Saved Key from the Hardware Vault."
    OnboardingState.BASIC_ENCRYPT -> "Tap Encrypt. Sigil uses 3 layers of encryption (AES+Twofish+Serpent) by default."
    OnboardingState.BASIC_OUTPUT -> "This is the 'Stealth Blob'. Copy it and send it. It can only be read with the password."
    OnboardingState.BASIC_ADVICE -> "NEVER share the password in the same chat as the message.\n\nShare it in person or use a different channel."
    OnboardingState.ADV_CUSTOM -> "For Architects: Manually add, remove, and reorder encryption layers here."
    OnboardingState.ADV_KEYSTORE -> "Keys saved here are encrypted by the Android TEE (Secure Hardware). We wipe them from RAM after use."
    OnboardingState.ADV_RELEASES -> "We are Open Source. Check this tab to audit every change we make."
    else -> ""
}

@Composable
fun ForkSelectionScreen(onBasic: () -> Unit, onAdvanced: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Security, null, Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(32.dp))
        Text("Sigil Setup", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(48.dp))

        Button(onClick = onBasic, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null)
                Spacer(Modifier.width(16.dp))
                Text("Quick Start (Standard)")
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onAdvanced, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, null)
                Spacer(Modifier.width(16.dp))
                Text("Architect Mode (Advanced)")
            }
        }
    }
}