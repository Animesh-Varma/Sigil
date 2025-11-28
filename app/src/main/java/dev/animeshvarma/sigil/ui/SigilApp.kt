package dev.animeshvarma.sigil.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.AppScreen
import dev.animeshvarma.sigil.model.SigilMode
import dev.animeshvarma.sigil.ui.components.LogsDialog
import dev.animeshvarma.sigil.ui.components.SigilDrawerContent
import dev.animeshvarma.sigil.ui.components.UnderConstructionView
import dev.animeshvarma.sigil.ui.screens.CustomEncryptionScreen
import dev.animeshvarma.sigil.ui.screens.DocsScreen
import dev.animeshvarma.sigil.ui.screens.EncryptionInterface
import dev.animeshvarma.sigil.ui.theme.bounceClick
import kotlinx.coroutines.launch
import dev.animeshvarma.sigil.model.UiState
@Composable
fun SigilApp(
    modifier: Modifier = Modifier,
    viewModel: SigilViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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
                    .padding(top = 1.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = { scope.launch { drawerState.open() } },
                    modifier = Modifier.align(Alignment.CenterStart).bounceClick()
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

            // --- ANIMATED CONTENT SWITCHER ---
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = uiState.currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "ScreenTransition"
                ) { targetScreen ->
                    when (targetScreen) {
                        AppScreen.HOME -> HomeContent(viewModel, uiState)
                        AppScreen.DOCS -> DocsScreen()
                        else -> UnderConstructionView()
                    }
                }
            }
        }

        if (uiState.showLogsDialog) {
            LogsDialog(
                logs = uiState.logs,
                onDismiss = { viewModel.onLogsClicked() },
                onClear = { viewModel.clearLogs() },
                onCopyLogs = { }
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

        // --- ANIMATED TAB SELECTOR ---
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(36.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clip(CircleShape)
                .padding(2.dp)
        ) {
            // Background Slider Logic
            val align by animateDpAsState(
                targetValue = if (uiState.selectedMode == SigilMode.AUTO) 0.dp else 100.dp, // Assuming 50% split visually, using Alignment is better but BoxWithConstraints is complex. Using Row weights approach below with animation.
                label = "TabSlide"
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // AUTO TAB
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (uiState.selectedMode == SigilMode.AUTO) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // Remove ripple for custom feel
                        ) { viewModel.onModeSelected(SigilMode.AUTO) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Auto",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (uiState.selectedMode == SigilMode.AUTO) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // CUSTOM TAB
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (uiState.selectedMode == SigilMode.CUSTOM) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.onModeSelected(SigilMode.CUSTOM) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Custom",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (uiState.selectedMode == SigilMode.CUSTOM) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        // Content Crossfade
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = uiState.selectedMode,
                transitionSpec = {
                    if (targetState == SigilMode.CUSTOM) {
                        slideInHorizontally { width -> width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> -width } + fadeOut()
                    } else {
                        slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                slideOutHorizontally { width -> width } + fadeOut()
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