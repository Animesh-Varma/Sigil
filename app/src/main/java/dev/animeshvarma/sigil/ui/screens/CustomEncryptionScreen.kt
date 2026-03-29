package dev.animeshvarma.sigil.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.AlgorithmRegistry
import dev.animeshvarma.sigil.model.EncryptionProfile
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.ui.components.KeepScreenShieldAwake
import dev.animeshvarma.sigil.ui.components.SecureAlertDialog
import dev.animeshvarma.sigil.ui.components.SecurePasswordInput
import dev.animeshvarma.sigil.ui.components.SigilButtonGroup
import dev.animeshvarma.sigil.ui.components.StyledLayerContainer
import dev.animeshvarma.sigil.ui.theme.AnimationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Suppress("AssignedValueIsNeverRead")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomEncryptionScreen(viewModel: SigilViewModel, uiState: UiState) {
    val context = LocalContext.current
    var showAddLayerSheet by remember { mutableStateOf(false) }
    var showSaveProfileDialog by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf<EncryptionProfile?>(null) }

    // Safety warnings for Raw Mode
    var showRawSecurityWarning by remember { mutableStateOf(false) }
    var pendingSaveData by remember { mutableStateOf<PendingProfileData?>(null) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                showAddLayerSheet = false
                showSaveProfileDialog = false
                showOverwriteDialog = null
                showRawSecurityWarning = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val listState = rememberLazyListState()
    val vaultEntries by viewModel.vaultEntries.collectAsState()

    // Layout Constants
    val spaceBetweenTopSections = 16.dp
    val spaceLayersToInput = 10.dp
    val spaceInputToPass = 10.dp
    val spacePassToButtons = 17.dp
    val spaceButtonsToOutput = 8.dp

    // Retrieve currently edited profile (if any) to preserve its KDF/Raw settings
    val editingProfile = remember(uiState.editingProfileId) {
        uiState.editingProfileId?.let { viewModel.getProfileById(it) }
    }

    Column(modifier = Modifier.fillMaxHeight()) {
        Spacer(modifier = Modifier.height(7.dp))

        // Compression Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Compression", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Switch(
                checked = uiState.isCompressionEnabled,
                onCheckedChange = { viewModel.toggleCompression(it) },
                modifier = Modifier.scale(0.8f)
            )
        }

        Spacer(modifier = Modifier.height(spaceBetweenTopSections))

        // --- LAYER MANAGER ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cipher Cascade", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row {
                    // SAVE PROFILE BUTTON
                    SmallFloatingActionButton(
                        onClick = {
                            // Always open the dialog to allow editing settings (Name, KDF, Raw)
                            showSaveProfileDialog = true
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        val icon = if (uiState.editingProfileId != null) Icons.Default.Edit else Icons.Default.Save
                        Icon(icon, "Save Profile", modifier = Modifier.size(16.dp))
                    }

                    Spacer(Modifier.width(8.dp))

                    // ADD LAYER BUTTON
                    SmallFloatingActionButton(
                        onClick = { showAddLayerSheet = true },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Add, "Add Layer", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Scrollable Layer List
            val showFadingEdge = uiState.customLayers.size > 3

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .then(if (showFadingEdge) Modifier.verticalFadingEdge() else Modifier)
            ) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .simpleVerticalScrollbar(listState)
                ) {
                    itemsIndexed(
                        items = uiState.customLayers,
                        key = { _, entry -> entry.id }
                    ) { index, entry ->

                        val scale = remember { Animatable(1f) }
                        val elevation = remember { Animatable(0f) }
                        val scope = rememberCoroutineScope()

                        fun triggerPopEffect() {
                            scope.launch {
                                launch { scale.animateTo(1.05f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                                launch { elevation.animateTo(8f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                            }
                            scope.launch {
                                delay(150)
                                launch { scale.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                                launch { elevation.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .animateItem(
                                    placementSpec = spring(
                                        stiffness = AnimationConfig.STIFFNESS,
                                        dampingRatio = AnimationConfig.DAMPING
                                    )
                                )
                                .zIndex(if (scale.value > 1f) 1f else 0f)
                                .graphicsLayer {
                                    scaleX = scale.value
                                    scaleY = scale.value
                                    shadowElevation = elevation.value
                                }
                        ) {
                            MovableLayerItem(
                                index = index,
                                total = uiState.customLayers.size,
                                name = entry.algorithm.name.replace("_", "-"),
                                onMoveUp = {
                                    triggerPopEffect()
                                    viewModel.moveLayer(index, index - 1)
                                },
                                onMoveDown = {
                                    triggerPopEffect()
                                    viewModel.moveLayer(index, index + 1)
                                },
                                onDelete = { viewModel.removeLayer(index) }
                            )
                        }
                    }
                }

                if (uiState.customLayers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No encryption layers selected.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spaceLayersToInput))

        // Input Field
        OutlinedTextField(
            value = uiState.customInput,
            onValueChange = { viewModel.onInputTextChanged(it) },
            label = { Text("Input Text") },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            )
        )

        Spacer(modifier = Modifier.height(spaceInputToPass))

        // Password Input
        SecurePasswordInput(
            value = uiState.customPassword,
            onValueChange = { viewModel.onPasswordChanged(it) },
            onSaveRequested = { name ->
                viewModel.saveToVault(alias = name, password = uiState.customPassword)
            },
            vaultEntries = vaultEntries,
            onEntrySelected = { viewModel.loadFromVault(it) },
            modifier = Modifier.fillMaxWidth().height(64.dp)
        )

        Spacer(modifier = Modifier.height(spacePassToButtons))

        // Action Buttons
        SigilButtonGroup(
            onLogs = { viewModel.onLogsClicked() },
            onEncrypt = { viewModel.onEncrypt() },
            onDecrypt = { viewModel.onDecrypt() }
        )

        Spacer(modifier = Modifier.height(spaceButtonsToOutput))

        // Output Field
        OutlinedTextField(
            value = uiState.customOutput,
            onValueChange = {},
            label = { Text("Output") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().height(100.dp),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            trailingIcon = {
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    IconButton(onClick = {
                        if (uiState.customOutput.isNotEmpty()) {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, uiState.customOutput)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Encrypted Message")
                            context.startActivity(shareIntent)
                            viewModel.addLog("Share Sheet opened.")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = {
                        // SECURE CLIPBOARD COPY
                        if (uiState.customOutput.isNotEmpty()) {
                            viewModel.copyToClipboardSecurely(uiState.customOutput, "Sigil Output")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showAddLayerSheet) {
        ModalBottomSheet(onDismissRequest = { showAddLayerSheet = false }) {
            KeepScreenShieldAwake(viewModel)
            AddLayerSheetContent(
                onAdd = { algos ->
                    viewModel.addLayers(algos)
                    showAddLayerSheet = false
                }
            )
        }
    }

    // --- DIALOGS ---
    if (showSaveProfileDialog) {
        // Prepare initial state from editing profile or defaults
        val defaultKdf = viewModel.getPrefs().let { CryptoEngine.KdfConfig(it.kdfIterations, it.kdfMemoryPow2, it.kdfParallelism) }
        val initialName = editingProfile?.name ?: ""
        val initialDesc = editingProfile?.description ?: ""
        val initialKdfOverride = editingProfile?.kdfConfig
        val initialIsRaw = editingProfile?.isRaw ?: false

        SaveProfileDialog(
            initialName = initialName,
            initialDescription = initialDesc,
            initialKdfOverride = initialKdfOverride,
            defaultKdf = defaultKdf,
            initialIsRaw = initialIsRaw,
            layerCount = uiState.customLayers.size,
            onDismiss = { showSaveProfileDialog = false },
            onSave = { name, desc, kdfOverride, isRaw ->
                // Check if Raw Mode is unsafe (no integrity/MAC)
                val currentAlgo = uiState.customLayers.firstOrNull()?.algorithm
                val isUnsafeRaw = isRaw &&
                        uiState.customLayers.size == 1 &&
                        currentAlgo != null &&
                        !CryptoEngine.isAEAD(currentAlgo)

                val performSave = {
                    if (editingProfile != null) {
                        viewModel.updateExistingProfile(
                            id = editingProfile.id,
                            name = name,
                            description = desc,
                            layers = uiState.customLayers.map { it.algorithm },
                            kdfOverride = kdfOverride,
                            compress = uiState.isCompressionEnabled,
                            isRaw = isRaw,
                            onSuccess = {
                                showSaveProfileDialog = false
                                viewModel.onModeSelected(SigilMode.AUTO)
                            }
                        )
                    } else {
                        viewModel.saveProfile(
                            name = name,
                            description = desc,
                            layers = uiState.customLayers.map { it.algorithm },
                            kdfOverride = kdfOverride,
                            compress = uiState.isCompressionEnabled,
                            isRaw = isRaw,
                            onSuccess = {
                                showSaveProfileDialog = false
                                viewModel.onModeSelected(SigilMode.AUTO)
                            },
                            onDuplicateName = { existing ->
                                showSaveProfileDialog = false
                                pendingSaveData = PendingProfileData(name, desc, kdfOverride, isRaw)
                                showOverwriteDialog = existing
                                }
                        )
                    }
                }

                if (isUnsafeRaw) {
                    pendingSaveData = PendingProfileData(name, desc, kdfOverride, true)
                    showRawSecurityWarning = true
                    showSaveProfileDialog = false
                } else {
                    performSave()
                }
            }
        )
    }

    if (showRawSecurityWarning) {
        val algoName = uiState.customLayers.firstOrNull()?.algorithm?.name ?: "Unknown"
        SecureAlertDialog(
            onDismissRequest = { showRawSecurityWarning = false },
            icon = { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Security Warning") },
            text = {
                Text(
                    "Raw Mode with $algoName lacks integrity checks (No MAC).\n\n" +
                            "Tampering with data will not be detected. \n\n" +
                            "Do you want to proceed anyway or return to use the standard chain?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Proceed anyway
                        pendingSaveData?.let { data ->
                            if (editingProfile != null) {
                                viewModel.updateExistingProfile(
                                    id = editingProfile.id,
                                    name = data.name,
                                    description = data.desc,
                                    layers = uiState.customLayers.map { it.algorithm },
                                    kdfOverride = data.kdf,
                                    compress = uiState.isCompressionEnabled,
                                    isRaw = data.isRaw,
                                    onSuccess = {
                                        viewModel.onModeSelected(SigilMode.AUTO)
                                    }
                                )
                            } else {
                                viewModel.saveProfile(
                                    name = data.name,
                                    description = data.desc,
                                    layers = uiState.customLayers.map { it.algorithm },
                                    kdfOverride = data.kdf,
                                    compress = uiState.isCompressionEnabled,
                                    isRaw = true,
                                    onSuccess = {
                                        viewModel.onModeSelected(SigilMode.AUTO)
                                    },
                                    onDuplicateName = { existing ->
                                        showOverwriteDialog = existing
                                    }
                                )
                            }
                        }
                        showRawSecurityWarning = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Proceed (Unsafe)") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRawSecurityWarning = false
                    }
                ) { Text("Cancel") }
            }
        )
    }

    // UPDATE CONFIRMATION DIALOG
    showOverwriteDialog?.let { existing ->

        SecureAlertDialog(
            onDismissRequest = { showOverwriteDialog = null },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Profile Exists") },
            text = { Text("A profile named '${existing.name}' already exists. Do you want to overwrite it?") },
            confirmButton = {
                Button(
                    onClick = {
                        val data = pendingSaveData
                        val updated = existing.copy(
                            name = data?.name ?: existing.name,
                            description = data?.desc ?: existing.description,
                            layers = uiState.customLayers.map { it.algorithm },
                            kdfConfig = data?.kdf ?: existing.kdfConfig,
                            isRaw = data?.isRaw ?: existing.isRaw,
                            isCompressionEnabled = uiState.isCompressionEnabled
                        )
                        viewModel.overwriteProfile(updated)
                        showOverwriteDialog = null
                        viewModel.onModeSelected(SigilMode.AUTO)
                        pendingSaveData = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

// Data holder for pending save
private data class PendingProfileData(
    val name: String,
    val desc: String,
    val kdf: CryptoEngine.KdfConfig?,
    val isRaw: Boolean
)

/**
 * Dialog that collects profile metadata and optional overrides used to save an encryption profile.
 *
 * Displays fields for profile name and description, an optional Raw Mode toggle (shown when `layerCount == 1`),
 * and an optional custom KDF section initialized from `initialKdf`. The Save button is enabled only when a
 * non-blank profile name is entered.
 *
 * @param initialName The starting name for the profile (e.g. when editing).
 * @param initialDescription The starting description for the profile.
 * @param initialKdfOverride The existing KDF override if any, to prefill the toggle and sliders.
 * @param defaultKdf The default/global KDF config to fallback to if no override is set.
 * @param initialIsRaw The starting raw mode state.
 * @param layerCount Number of layers in the current profile; controls whether the Raw Mode option is shown.
 * @param onDismiss Callback invoked when the user cancels or dismisses the dialog.
 * @param onSave Callback invoked when the user confirms the save. Receives the profile name, short description,
 *               an optional `CryptoEngine.KdfConfig` (null when KDF override is not used), and a Boolean indicating
 *               whether Raw Mode is enabled.
 */
@Composable
fun SaveProfileDialog(
    initialName: String = "",
    initialDescription: String = "",
    initialKdfOverride: CryptoEngine.KdfConfig?,
    defaultKdf: CryptoEngine.KdfConfig,
    initialIsRaw: Boolean = false,
    layerCount: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, CryptoEngine.KdfConfig?, Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }

    // KDF Override State
    var useCustomKdf by remember { mutableStateOf(initialKdfOverride != null) }
    val effectiveKdf = initialKdfOverride ?: defaultKdf

    var kdfIter by remember { mutableFloatStateOf(effectiveKdf.iterations.toFloat()) }
    var kdfMem by remember { mutableFloatStateOf(effectiveKdf.memoryPow2.toFloat()) }
    var kdfPar by remember { mutableFloatStateOf(effectiveKdf.parallelism.toFloat()) }

    // Raw Mode State
    var useRawMode by remember { mutableStateOf(initialIsRaw) }

    SecureAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialName.isEmpty()) "Save Profile" else "Edit Profile") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 20) name = it },
                    label = { Text("Profile Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 50) description = it },
                    label = { Text("Short Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // RAW MODE TOGGLE
                if (layerCount == 1) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (useRawMode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { useRawMode = !useRawMode }
                            .padding(4.dp)
                    ) {
                        Checkbox(checked = useRawMode, onCheckedChange = { useRawMode = it })
                        Column {
                            Text("Raw Mode", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "Raw output only. No metadata.\n" +
                                        "Auto-decrypt unsupported; requires manual profile selection.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // --- CUSTOM KDF SECTION ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (useCustomKdf) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { useCustomKdf = !useCustomKdf }
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    ) {
                        Checkbox(checked = useCustomKdf, onCheckedChange = { useCustomKdf = it })
                        Column {
                            Text(
                                "Override Key Derivation (KDF)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Fine-tune trade-offs.\n" +
                                        "Auto-decrypt unsupported; requires manual config match.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    AnimatedVisibility(visible = useCustomKdf) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                        ) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            Text("Iterations: ${kdfIter.toInt()}", style = MaterialTheme.typography.labelSmall)
                            Slider(value = kdfIter, onValueChange = { kdfIter = it }, valueRange = 1f..32f)

                            Text("Memory: ${(1 shl kdfMem.toInt()) / 1024} MB", style = MaterialTheme.typography.labelSmall)
                            Slider(value = kdfMem, onValueChange = { kdfMem = it }, valueRange = 12f..18f)

                            Text("Parallelism: ${kdfPar.toInt()}", style = MaterialTheme.typography.labelSmall)
                            Slider(value = kdfPar, onValueChange = { kdfPar = it }, valueRange = 1f..8f)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val kdfConfig = if (useCustomKdf) {
                        CryptoEngine.KdfConfig(kdfIter.toInt(), kdfMem.toInt(), kdfPar.toInt())
                    } else null
                    onSave(name, description, kdfConfig, useRawMode)
                },
                enabled = name.isNotBlank() && layerCount > 0
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Movable Layer Row
@Composable
fun MovableLayerItem(
    index: Int,
    total: Int,
    name: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    StyledLayerContainer(modifier = Modifier.padding(2.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Layer ${index + 1}: $name",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, null, tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent)
                }
                IconButton(onClick = onMoveDown, enabled = index < total - 1, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = if (index < total - 1) MaterialTheme.colorScheme.onSurfaceVariant else Color.Transparent)
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// Add Layer Sheet
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AddLayerSheetContent(onAdd: (List<CryptoEngine.Algorithm>) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var searchDescription by remember { mutableStateOf(false) }
    var selectedAlgos by remember { mutableStateOf(setOf<CryptoEngine.Algorithm>()) }
    val allAlgos = AlgorithmRegistry.supportedAlgorithms
    val focusManager = LocalFocusManager.current

    val filteredAlgos = remember(searchQuery, searchDescription, allAlgos) {
        allAlgos.filter { algo ->
            val nameMatch = algo.name.contains(searchQuery, ignoreCase = true)
            if (searchDescription) nameMatch || algo.description.contains(searchQuery, ignoreCase = true) else nameMatch
        }
    }

    Column(modifier = Modifier.padding(top = 16.dp)) {

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Add Encryption Layer", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(if (isPressed) 0.9f else 1f, label = "ButtonBounce")

            Button(
                onClick = { onAdd(selectedAlgos.toList()) },
                enabled = selectedAlgos.isNotEmpty(),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                interactionSource = interactionSource,
                modifier = Modifier
                    .height(32.dp)
                    .scale(scale)
            ) {
                Text(text = "Add${if (selectedAlgos.isNotEmpty()) " (${selectedAlgos.size})" else ""}", fontSize = 13.sp)
            }
        }

        // Search Bar
        Box(modifier = Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search (e.g. AES, Serpent)...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton(onClick = { focusManager.clearFocus() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Done") } },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                singleLine = true
            )
        }

        // Toggle
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { searchDescription = !searchDescription }
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Switch(checked = searchDescription, onCheckedChange = { searchDescription = it }, modifier = Modifier.scale(0.7f))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Include description in search", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            CompositionLocalProvider(
                LocalOverscrollFactory provides null
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
                ) {
                    items(
                        items = filteredAlgos,
                        key = { it.id }
                    ) { algoData ->
                        val engineEnum = runCatching { CryptoEngine.Algorithm.valueOf(algoData.id) }.getOrNull()
                            ?: return@items // Skip unknown algorithms
                        val isSelected = selectedAlgos.contains(engineEnum)
                        val isWeak = algoData.isWeak
                        val containerColor = when {
                            isSelected -> MaterialTheme.colorScheme.secondaryContainer
                            isWeak -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        }

                        Surface(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = containerColor
                        ) {
                            ListItem(
                                headlineContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            algoData.name,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (isWeak) {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                supportingContent = {
                                    Column {
                                        Text(algoData.description)
                                        if (isWeak) {
                                            Text(algoData.securityWarning ?: "Weak Cipher", color = MaterialTheme.colorScheme.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                },
                                trailingContent = {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedAlgos = if (checked) selectedAlgos + engineEnum else selectedAlgos - engineEnum
                                        }
                                    )
                                },
                                modifier = Modifier.clickable {
                                    selectedAlgos = if (isSelected) selectedAlgos - engineEnum else selectedAlgos + engineEnum
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp, bottom = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.width(40.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "END OF LIST",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Custom Scrollbar Modifier
@Composable
fun Modifier.simpleVerticalScrollbar(
    state: LazyListState,
    width: Dp = 4.dp
): Modifier {
    val targetAlpha = if (state.isScrollInProgress) 1f else 0f
    val duration = if (state.isScrollInProgress) 150 else 500

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration)
    )

    return drawWithContent {
        drawContent()

        val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0f

        if (needDrawScrollbar) {
            val totalItems = state.layoutInfo.totalItemsCount
            if (totalItems == 0) return@drawWithContent

            val elementHeight = this.size.height / totalItems
            val firstVisibleElementIndex = state.firstVisibleItemIndex
            val visibleItemsCount = state.layoutInfo.visibleItemsInfo.size

            // Calculate scrollbar
            val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
            val scrollbarHeight = visibleItemsCount * elementHeight

            drawRoundRect(
                color = Color.Gray.copy(alpha = 0.5f * alpha),
                topLeft = Offset(this.size.width - width.toPx(), scrollbarOffsetY),
                size = Size(width.toPx(), scrollbarHeight),
                cornerRadius = CornerRadius(width.toPx() / 2)
            )
        }
    }
}

// Fading Edge Modifier
fun Modifier.verticalFadingEdge(): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadeHeight = 40.dp.toPx()
        val height = size.height

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = height - fadeHeight,
                endY = height
            ),
            blendMode = BlendMode.DstIn
        )
    }