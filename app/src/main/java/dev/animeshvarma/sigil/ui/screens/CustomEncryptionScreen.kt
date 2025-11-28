package dev.animeshvarma.sigil.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.AlgorithmRegistry
import dev.animeshvarma.sigil.ui.theme.bounceClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomEncryptionScreen(viewModel: SigilViewModel, uiState: UiState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showAddLayerSheet by remember { mutableStateOf(false) }

    // Spacing
    val spaceBetweenTopSections = 16.dp
    val spaceLayersToInput = 10.dp
    val spaceInputToPass = 10.dp
    val spacePassToButtons = 17.dp
    val spaceButtonsToOutput = 8.dp

    Column(modifier = Modifier.fillMaxHeight()) {

        // --- COMPRESSION ---
        Spacer(modifier = Modifier.height(7.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Compression", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            }
            Switch(
                checked = uiState.isCompressionEnabled,
                onCheckedChange = { viewModel.toggleCompression(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
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
                Text("Layers", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)

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

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 150.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(uiState.customLayers) { index, algo ->
                    DraggableLayerItem(
                        index = index,
                        name = algo.name.replace("_", "-"),
                        onMoveUp = { viewModel.moveLayer(index, index - 1) },
                        onMoveDown = { viewModel.moveLayer(index, index + 1) },
                        onDelete = { viewModel.removeLayer(index) }
                    )
                }
                if (uiState.customLayers.isEmpty()) {
                    item {
                        Text(
                            "No encryption layers added.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spaceLayersToInput))

        // --- INPUTS ---
        OutlinedTextField(
            value = uiState.customInput,
            onValueChange = { viewModel.onInputTextChanged(it) },
            label = { Text("Text to encrypt/decrypt") },
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

        OutlinedTextField(
            value = uiState.customPassword,
            onValueChange = { viewModel.onPasswordChanged(it) },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(24.dp),
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            )
        )

        Spacer(modifier = Modifier.height(spacePassToButtons))

        // Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.onLogsClicked() },
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Logs", color = MaterialTheme.colorScheme.primary)
            }

            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onEncrypt() },
                    modifier = Modifier.weight(1f).height(48.dp).bounceClick(),
                    shape = CircleShape
                ) { Text("Encrypt") }

                Button(
                    onClick = { viewModel.onDecrypt() },
                    modifier = Modifier.weight(1f).height(48.dp).bounceClick(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) { Text("Decrypt") }
            }
        }

        Spacer(modifier = Modifier.height(spaceButtonsToOutput))

        // Output
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
                IconButton(onClick = {
                    if (uiState.customOutput.isNotEmpty()) {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Sigil Output", uiState.customOutput)
                        clipboard.setPrimaryClip(clip)

                        viewModel.addLog("Copied to clipboard")
                    }
                }) {
                    Icon(Icons.Default.ContentCopy, "Copy")
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showAddLayerSheet) {
        ModalBottomSheet(onDismissRequest = { showAddLayerSheet = false }) {
            AddLayerSheetContent(
                onSelect = { algo ->
                    viewModel.addLayer(algo)
                    showAddLayerSheet = false
                }
            )
        }
    }
}

// [NEW] Draggable Layer Item
@Composable
fun DraggableLayerItem(
    index: Int,
    name: String,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    // Basic drag detection state
    var verticalDragOffset by remember { mutableFloatStateOf(0f) }

    // Threshold to trigger a move (roughly the height of an item + padding)
    val dragThreshold = 50f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("Layer ${index + 1}: $name", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Drag Handle with Gesture Detector
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = { verticalDragOffset = 0f },
                            onDragCancel = { verticalDragOffset = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            verticalDragOffset += dragAmount.y

                            // Simple threshold-based swapping
                            if (verticalDragOffset < -dragThreshold) {
                                onMoveUp()
                                verticalDragOffset = 0f // Reset after trigger
                            } else if (verticalDragOffset > dragThreshold) {
                                onMoveDown()
                                verticalDragOffset = 0f
                            }
                        }
                    }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, // Muted color per design
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

@Composable
fun AddLayerSheetContent(onSelect: (CryptoEngine.Algorithm) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var searchDescription by remember { mutableStateOf(false) }
    val allAlgos = AlgorithmRegistry.supportedAlgorithms
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Add Encryption Layer", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search algorithms...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                IconButton(onClick = { focusManager.clearFocus() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Done")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            singleLine = true
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clickable { searchDescription = !searchDescription }
        ) {
            Switch(
                checked = searchDescription,
                onCheckedChange = { searchDescription = it },
                modifier = Modifier.scale(0.7f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Include description in search",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn {
            val filtered = allAlgos.filter { algo ->
                val nameMatch = algo.name.contains(searchQuery, ignoreCase = true)
                if (searchDescription) {
                    nameMatch || algo.description.contains(searchQuery, ignoreCase = true)
                } else {
                    nameMatch
                }
            }

            items(filtered) { algoData ->
                val engineEnum = CryptoEngine.Algorithm.valueOf(algoData.id)
                ListItem(
                    headlineContent = { Text(algoData.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(algoData.description) },
                    modifier = Modifier.clickable { onSelect(engineEnum) }
                )
                HorizontalDivider()
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}