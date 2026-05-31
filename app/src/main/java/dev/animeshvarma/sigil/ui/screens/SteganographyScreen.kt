package dev.animeshvarma.sigil.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.model.TextStegoMethod
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.ui.components.KeepScreenShieldAwake
import dev.animeshvarma.sigil.ui.components.SecurePasswordInput
import dev.animeshvarma.sigil.ui.components.SigilSegmentedControl
import dev.animeshvarma.sigil.ui.components.StyledLayerContainer
import dev.animeshvarma.sigil.ui.components.UnderConstructionView
import dev.animeshvarma.sigil.ui.theme.AnimationConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteganographyScreen(viewModel: SigilViewModel, uiState: UiState) {
    var selectedMediaTabIndex by remember { mutableIntStateOf(0) }
    val mediaTabs = listOf("Text", "Image", "Video")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Primary Media Tab Selector
        SigilSegmentedControl(
            items = mediaTabs,
            selectedIndex = selectedMediaTabIndex,
            onItemSelection = { selectedMediaTabIndex = it },
            modifier = Modifier.fillMaxWidth(0.9f)
        )

        Spacer(modifier = Modifier.height(15.dp))

        // 2. Animated Content Area
        Box(modifier = Modifier.fillMaxSize()) {
            val slideSpring = spring<IntOffset>(
                stiffness = AnimationConfig.STIFFNESS,
                dampingRatio = AnimationConfig.DAMPING
            )

            AnimatedContent(
                targetState = selectedMediaTabIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(animationSpec = slideSpring) { it } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = slideSpring) { -it } + fadeOut()
                    } else {
                        slideInHorizontally(animationSpec = slideSpring) { -it } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = slideSpring) { it } + fadeOut()
                    }
                },
                label = "StegoTabTransition"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> TextSteganographyInterface(viewModel, uiState) // Core Text Implementation
                    1 -> UnderConstructionView()
                    2 -> UnderConstructionView()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextSteganographyInterface(viewModel: SigilViewModel, uiState: UiState) {
    val context = LocalContext.current
    val vaultEntries by viewModel.vaultEntries.collectAsState()

    var showProfileSheet by remember { mutableStateOf(false) }
    val currentToast = remember { mutableStateOf<Toast?>(null) }
    val showToast: (String) -> Unit = { message ->
        currentToast.value?.cancel()
        val toast = Toast.makeText(context, message, Toast.LENGTH_SHORT)
        currentToast.value = toast
        toast.show()
    }

    // Lifecycle safety for sheet
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) showProfileSheet = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val textMethods = TextStegoMethod.values().toList()
    val selectedMethodIndex = textMethods.indexOf(uiState.selectedStegoMethod)

    Column(
        modifier = Modifier
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Method Selector
        SigilSegmentedControl(
            items = textMethods.map { it.title },
            selectedIndex = selectedMethodIndex,
            onItemSelection = { viewModel.setStegoMethod(textMethods[it]) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.selectedStegoMethod == TextStegoMethod.WHITESPACE_PLACEHOLDER || uiState.selectedStegoMethod == TextStegoMethod.SYNONYM_LLM_PLACEHOLDER) {
            Box(modifier = Modifier.weight(1f)) {
                UnderConstructionView()
            }
        } else {
            // --- ACTIVE ENGINES FORM ---

            // Cover Text
            OutlinedTextField(
                value = uiState.stegoCoverText,
                onValueChange = { viewModel.onStegoCoverTextChanged(it) },
                label = { Text("Carrier Text") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            )

            Spacer(modifier = Modifier.height(7.dp))

            // Secret Text
            OutlinedTextField(
                value = uiState.stegoSecretText,
                onValueChange = { viewModel.onStegoSecretTextChanged(it) },
                label = { Text("Payload") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            )

            Spacer(modifier = Modifier.height(15.dp))

            // Cryptography Suite Integration
            StyledLayerContainer {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleStegoEncryption(!uiState.isStegoEncryptionEnabled) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Cryptographic Layer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Encrypt the payload prior to concealment",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isStegoEncryptionEnabled,
                            onCheckedChange = { viewModel.toggleStegoEncryption(it) }
                        )
                    }

                    // Vault and Profile Selection
                    AnimatedVisibility(
                        visible = uiState.isStegoEncryptionEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            SecurePasswordInput(
                                value = uiState.stegoPassword,
                                onValueChange = { viewModel.onStegoPasswordChanged(it) },
                                onSaveRequested = { name -> viewModel.saveToVault(name, uiState.stegoPassword) },
                                vaultEntries = vaultEntries,
                                onEntrySelected = { viewModel.loadFromVault(it) },
                                modifier = Modifier.fillMaxWidth().height(64.dp)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedButton(
                                onClick = { showProfileSheet = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text("Active Configuration: ${uiState.activeProfile.name}", color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(15.dp))

            // Custom Action Row for Steganography
            StegoActionGroup(
                onLogs = { viewModel.onLogsClicked() },
                onHide = { viewModel.onEncodeSteganography() },
                onExtract = { viewModel.onDecodeSteganography() }
            )

            Spacer(modifier = Modifier.height(7.dp))

            // Output Field
            OutlinedTextField(
                value = uiState.stegoOutput,
                onValueChange = { },
                label = { Text("Output") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth().weight(1.2f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                trailingIcon = {
                    Column(modifier = Modifier.padding(end = 4.dp)) {
                        IconButton(onClick = {
                            if (uiState.stegoOutput.isNotEmpty()) {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, uiState.stegoOutput)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Output"))
                                viewModel.addLog("Sharing options opened.")
                            }
                        }) {
                            Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        IconButton(onClick = {
                            if (uiState.stegoOutput.isNotEmpty()) {
                                viewModel.copyToClipboardSecurely(uiState.stegoOutput, "Extracted Payload")
                            }
                        }) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // --- EXACT PROFILE SHEET FROM ENCRYPTION SCREEN ---
    if (showProfileSheet) {
        ModalBottomSheet(onDismissRequest = { showProfileSheet = false }) {
            KeepScreenShieldAwake()
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Encryption Profiles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Active: ${uiState.activeProfile.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    FilledTonalButton(onClick = {
                        showProfileSheet = false
                        viewModel.onModeSelected(SigilMode.CUSTOM)
                        showToast("Switched to Custom Mode")
                    }) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Create")
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    items(items = uiState.availableProfiles, key = { it.id }) { profile ->
                        // Re-using the public ExpandableProfileCard defined in EncryptionScreen.kt
                        ExpandableProfileCard(
                            profile = profile,
                            isActive = profile.id == uiState.activeProfile.id,
                            onSelect = {
                                viewModel.selectProfile(it)
                                showToast("Profile activated: ${it.name}")
                            },
                            onEdit = {
                                viewModel.loadProfileToCustomMode(it)
                                showProfileSheet = false
                                showToast("Modifying profile: ${it.name}")
                            },
                            onDelete = {
                                viewModel.deleteProfile(it.id)
                                showToast("Profile successfully removed.")
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * A custom action group specific to Steganography to avoid the terms "Encrypt/Decrypt".
 * Built matching the mechanics of SigilButtonGroup.
 */
@Composable
private fun StegoActionGroup(
    onLogs: () -> Unit,
    onHide: () -> Unit,
    onExtract: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StegoPill(
            text = "Logs",
            onClick = onLogs,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            baseWeight = 0.65f
        )
        StegoPill(
            text = "Conceal",
            onClick = onHide,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            border = null,
            baseWeight = 1f
        )
        StegoPill(
            text = "Extract",
            onClick = onExtract,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            border = null,
            baseWeight = 1f
        )
    }
}

@Composable
private fun RowScope.StegoPill(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke?,
    baseWeight: Float
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val targetWeight = if (isPressed) baseWeight * 1.15f else baseWeight

    val weight by animateFloatAsState(
        targetValue = targetWeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PillExpansion"
    )

    Surface(
        modifier = Modifier.weight(weight).fillMaxHeight(),
        shape = CircleShape,
        color = containerColor,
        border = border,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}