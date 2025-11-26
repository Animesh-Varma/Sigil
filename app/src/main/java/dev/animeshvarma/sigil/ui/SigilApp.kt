package dev.animeshvarma.sigil.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.UiState
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.ui.components.LogsDialog
import dev.animeshvarma.sigil.ui.components.SigilDrawerContent
import dev.animeshvarma.sigil.ui.components.UnderConstructionView
import dev.animeshvarma.sigil.ui.screens.DocsScreen
import dev.animeshvarma.sigil.ui.screens.EncryptionInterface
import kotlinx.coroutines.launch

@Composable
fun SigilApp(
    modifier: Modifier = Modifier,
    viewModel: SigilViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // [FIX 1] Initialize Clipboard Manager here
    val clipboardManager = LocalClipboardManager.current

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SigilDrawerContent(
                currentScreen = uiState.currentScreen,
                onScreenSelected = { screen ->
                    viewModel.onScreenSelected(screen)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- HEADER ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                val headerTitle = when (uiState.currentScreen) {
                    AppScreen.HOME, AppScreen.DOCS -> "Sigil"
                    else -> uiState.currentScreen.title
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = headerTitle,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // --- CONTENT SWITCHER ---
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.currentScreen) {
                    AppScreen.HOME -> HomeContent(viewModel, uiState)
                    AppScreen.DOCS -> DocsScreen()
                    else -> UnderConstructionView()
                }
            }
        }

        if (uiState.showLogsDialog) {
            LogsDialog(
                logs = uiState.logs,
                onDismiss = { viewModel.onLogsClicked() },
                onClear = { viewModel.clearLogs() },
                // [FIX 2] Implement Copy Logic using the clipboardManager
                onCopyLogs = {
                    val fullLog = uiState.logs.joinToString("\n")
                    clipboardManager.setText(AnnotatedString(fullLog))
                    viewModel.addLog("Full logs copied")
                }
            )
        }
    }
}

@Composable
fun HomeContent(viewModel: SigilViewModel, uiState: UiState) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(36.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clip(CircleShape)
                .padding(2.dp)
        ) {
            SigilMode.entries.forEach { mode ->
                val isSelected = uiState.selectedMode == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { viewModel.onModeSelected(mode) }
                ) {
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.selectedMode == SigilMode.AUTO) {
                EncryptionInterface(viewModel, uiState)
            } else {
                UnderConstructionView()
            }
        }
    }
}