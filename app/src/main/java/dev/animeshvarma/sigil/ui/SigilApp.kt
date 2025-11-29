package dev.animeshvarma.sigil.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.ui.components.LogsDialog
import dev.animeshvarma.sigil.ui.components.SigilDrawerContent
import dev.animeshvarma.sigil.ui.components.UnderConstructionView
import dev.animeshvarma.sigil.ui.screens.CustomEncryptionScreen
import dev.animeshvarma.sigil.ui.screens.DocsScreen
import dev.animeshvarma.sigil.ui.screens.EncryptionInterface
import dev.animeshvarma.sigil.ui.theme.AnimationConfig
import kotlinx.coroutines.launch

@Composable
fun SigilApp(
    modifier: Modifier = Modifier,
    viewModel: SigilViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // HEADER
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
                        Icon(Icons.Default.Menu, "Menu", tint = MaterialTheme.colorScheme.onSurface)
                    }

                    val headerTitle = when (uiState.currentScreen) {
                        AppScreen.HOME, AppScreen.DOCS -> "Sigil"
                        else -> uiState.currentScreen.title
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(headerTitle, fontSize = 20.sp, fontWeight = FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(Modifier.width(32.dp).height(2.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Screen Transition Animation
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = uiState.currentScreen,
                        transitionSpec = {
                            val screenSpring = spring<Float>(
                                stiffness = AnimationConfig.STIFFNESS,
                                dampingRatio = AnimationConfig.DAMPING
                            )

                            fadeIn(animationSpec = screenSpring) + scaleIn(initialScale = 0.95f, animationSpec = screenSpring) togetherWith
                                    fadeOut(animationSpec = screenSpring)
                        },
                        label = "ScreenTransition"
                    ) { target ->
                        when (target) {
                            AppScreen.HOME -> HomeContent(viewModel, uiState)
                            AppScreen.DOCS -> DocsScreen()
                            else -> UnderConstructionView()
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (uiState.showLogsDialog) {
            LogsDialog(
                logs = uiState.logs,
                onDismiss = { viewModel.onLogsClicked() },
                onClear = { viewModel.clearLogs() },
                onCopyLogs = {
                    val fullLog = uiState.logs.joinToString("\n")
                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                    val clip = android.content.ClipData.newPlainText("Sigil Logs", fullLog)
                    clipboard.setPrimaryClip(clip)
                    viewModel.addLog("Full logs copied")
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(viewModel: SigilViewModel, uiState: UiState) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth(0.7f)) {
            SegmentedButton(
                selected = uiState.selectedMode == SigilMode.AUTO,
                onClick = { viewModel.onModeSelected(SigilMode.AUTO) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text("Auto") }
            SegmentedButton(
                selected = uiState.selectedMode == SigilMode.CUSTOM,
                onClick = { viewModel.onModeSelected(SigilMode.CUSTOM) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text("Custom") }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Box(modifier = Modifier.fillMaxSize()) {

            val slideSpring = spring<IntOffset>(
                stiffness = AnimationConfig.STIFFNESS,
                dampingRatio = AnimationConfig.DAMPING
            )

            AnimatedContent(
                targetState = uiState.selectedMode,
                transitionSpec = {
                    if (targetState == SigilMode.CUSTOM) {
                        slideInHorizontally(animationSpec = slideSpring) { it } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = slideSpring) { -it } + fadeOut()
                    } else {
                        slideInHorizontally(animationSpec = slideSpring) { -it } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = slideSpring) { it } + fadeOut()
                    }
                },
                label = "TabTransition"
            ) { mode ->
                if (mode == SigilMode.AUTO) {
                    EncryptionInterface(viewModel, uiState)
                } else {
                    CustomEncryptionScreen(viewModel, uiState)
                }
            }
        }
    }
}