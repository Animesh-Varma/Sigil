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
import androidx.compose.ui.draw.scale
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
import dev.animeshvarma.sigil.ui.components.SigilSegmentedControl
import dev.animeshvarma.sigil.ui.components.UnderConstructionView
import dev.animeshvarma.sigil.ui.screens.CustomEncryptionScreen
import dev.animeshvarma.sigil.ui.screens.DocsScreen
import dev.animeshvarma.sigil.ui.screens.EncryptionInterface
import dev.animeshvarma.sigil.ui.screens.KeystoreScreen
import dev.animeshvarma.sigil.ui.screens.SteganographyScreen
import dev.animeshvarma.sigil.ui.theme.AnimationConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

                // CONTENT SWITCHER
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
                            AppScreen.STEGANOGRAPHY -> SteganographyScreen()
                            AppScreen.KEYSTORE -> KeystoreScreen(viewModel)
                            AppScreen.HEADERLESS,
                            AppScreen.FILE_ENCRYPTION,
                            AppScreen.ASYMMETRIC,
                            AppScreen.PARTITIONS -> UnderConstructionView()
                            else -> UnderConstructionView()
                        }
                    }
                }
            }

            // The Expressive Shape-Shifting Loader
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.scale(1.4f)
                    )
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
        SigilSegmentedControl(
            items = listOf("Auto", "Custom"),
            selectedIndex = if (uiState.selectedMode == SigilMode.AUTO) 0 else 1,
            onItemSelection = { index ->
                val newMode = if (index == 0) SigilMode.AUTO else SigilMode.CUSTOM
                viewModel.onModeSelected(newMode)
            },
            modifier = Modifier.fillMaxWidth(0.65f)
        )

        Spacer(modifier = Modifier.height(15.dp))

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