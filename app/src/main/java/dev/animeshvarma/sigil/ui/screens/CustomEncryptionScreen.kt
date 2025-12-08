package dev.animeshvarma.sigil.ui.screens

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.crypto.CryptoEngine
import dev.animeshvarma.sigil.model.AlgorithmRegistry
import dev.animeshvarma.sigil.model.SigilAlgorithm
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.ui.components.SigilButtonGroup
import dev.animeshvarma.sigil.ui.components.StyledLayerContainer
import dev.animeshvarma.sigil.ui.components.SecurePasswordInput
import dev.animeshvarma.sigil.ui.theme.AnimationConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomEncryptionScreen(viewModel: SigilViewModel, uiState: UiState) {
    val context = LocalContext.current
    var showAddLayerSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val vaultEntries by viewModel.vaultEntries.collectAsState()

    // Spacing
    val spaceBetweenTopSections = 16.dp
    val spaceLayersToInput = 10.dp
    val spaceInputToPass = 10.dp
    val spacePassToButtons = 17.dp
    val spaceButtonsToOutput = 8.dp

    Column(modifier = Modifier.fillMaxHeight()) {
        Spacer(modifier = Modifier.height(7.dp))

        // Compression
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

            // Scrollable Area
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

                        // Local Animation State
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
                                    placementSpec = spring<IntOffset>(
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
                    Text(
                        "No encryption layers added.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spaceLayersToInput))

        // Inputs
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

        // Actions
        SigilButtonGroup(
            onLogs = { viewModel.onLogsClicked() },
            onEncrypt = { viewModel.onEncrypt() },
            onDecrypt = { viewModel.onDecrypt() }
        )

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
                        if (uiState.customOutput.isNotEmpty()) {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            val clip = android.content.ClipData.newPlainText("Sigil Output", uiState.customOutput)
                            clipboard.setPrimaryClip(clip)
                            viewModel.addLog("Copied to clipboard")
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
            AddLayerSheetContent(
                onAdd = { algos ->
                    viewModel.addLayers(algos)
                    showAddLayerSheet = false
                }
            )
        }
    }
}

// MovableLayerItem
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
            Text("Layer ${index + 1}: $name", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)

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

@Composable
fun AddLayerSheetContent(onAdd: (List<CryptoEngine.Algorithm>) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var searchDescription by remember { mutableStateOf(false) }
    var selectedAlgos by remember { mutableStateOf(setOf<CryptoEngine.Algorithm>()) }
    val allAlgos = AlgorithmRegistry.supportedAlgorithms
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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
                Text(
                    text = "Add${if (selectedAlgos.isNotEmpty()) " (${selectedAlgos.size})" else ""}",
                    fontSize = 13.sp
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search algorithms...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { IconButton(onClick = { focusManager.clearFocus() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Done") } },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            singleLine = true
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { searchDescription = !searchDescription }
        ) {
            Switch(checked = searchDescription, onCheckedChange = { searchDescription = it }, modifier = Modifier.scale(0.7f))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Include description in search", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn {
                val filtered = allAlgos.filter { algo ->
                    val nameMatch = algo.name.contains(searchQuery, ignoreCase = true)
                    if (searchDescription) nameMatch || algo.description.contains(searchQuery, ignoreCase = true) else nameMatch
                }

                items(filtered) { algoData ->
                    val engineEnum = CryptoEngine.Algorithm.valueOf(algoData.id)
                    val isSelected = selectedAlgos.contains(engineEnum)

                    Surface(
                        modifier = Modifier.padding(vertical = 4.dp).clip(RoundedCornerShape(16.dp)),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    algoData.name,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            supportingContent = { Text(algoData.description) },
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
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
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

        val firstVisibleElementIndex = state.firstVisibleItemIndex
        val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0f

        if (needDrawScrollbar) {
            val totalItems = state.layoutInfo.totalItemsCount
            if (totalItems == 0) return@drawWithContent

            val elementHeight = this.size.height / totalItems
            val scrollbarOffsetY = firstVisibleElementIndex * elementHeight
            val scrollbarHeight = state.layoutInfo.visibleItemsInfo.size * elementHeight

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

        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                0.8f to Color.Black,
                1f to Color.Transparent
            ),
            blendMode = BlendMode.DstIn
        )
    }