package dev.animeshvarma.sigil.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale // Added standard import
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.AlgorithmRegistry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomEncryptionScreen(viewModel: SigilViewModel, uiState: UiState) {
    val clipboardManager = LocalClipboardManager.current
    var showAddLayerSheet by remember { mutableStateOf(false) }

    val spaceBetweenTopSections = 16.dp
    val spaceLayersToInput = 10.dp
    val spaceInputToPass = 10.dp
    val spacePassToButtons = 16.dp
    val spaceButtonsToOutput = 8.dp

    Column(
        modifier = Modifier.fillMaxHeight()
        // Removed verticalArrangement to use manual spacers below
    ) {
        // --- COMPRESSION TOGGLE ---
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
                    LayerItem(
                        index = index,
                        name = algo.name.replace("_", "-"),
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
            label = { Text("Input Text") },
            modifier = Modifier
                .weight(1f) // Takes all available space
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            )
        )

        Spacer(modifier = Modifier.height(spaceInputToPass))

        // Password
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

            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.onEncrypt() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape
                ) { Text("Encrypt") }

                Button(
                    onClick = { viewModel.onDecrypt() },
                    modifier = Modifier.weight(1f).height(48.dp),
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
                        clipboardManager.setText(AnnotatedString(uiState.customOutput))
                        viewModel.addLog("Copied to clipboard")
                    }
                }) {
                    Icon(Icons.Default.ContentCopy, "Copy")
                }
            }
        )

        Spacer(modifier = Modifier.height(8.dp)) // Bottom edge safety
    }

    // --- BOTTOM SHEET ---
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

// ... LayerItem and AddLayerSheetContent (Unchanged) ...
@Composable
fun LayerItem(index: Int, name: String, onDelete: () -> Unit) {
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
        Row {
            Icon(Icons.Default.DragIndicator, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                Icons.Default.Delete,
                "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable { onDelete() }
            )
        }
    }
}

@Composable
fun AddLayerSheetContent(onSelect: (CryptoEngine.Algorithm) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val allAlgos = AlgorithmRegistry.supportedAlgorithms

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Add Encryption Layer", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search algorithms...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            val filtered = allAlgos.filter {
                it.name.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true)
            }

            items(filtered) { algoData ->
                val engineEnum = CryptoEngine.Algorithm.valueOf(algoData.id)
                ListItem(
                    headlineContent = { Text(algoData.name) },
                    supportingContent = { Text(algoData.description) },
                    modifier = Modifier.clickable { onSelect(engineEnum) }
                )
                HorizontalDivider()
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}