package dev.animeshvarma.sigil.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.animeshvarma.sigil.ui.components.SigilSegmentedControl
import dev.animeshvarma.sigil.ui.components.UnderConstructionView
import dev.animeshvarma.sigil.ui.theme.AnimationConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Docs", "Releases")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SigilSegmentedControl(
            items = tabs,
            selectedIndex = selectedTabIndex,
            onItemSelection = { selectedTabIndex = it },
            modifier = Modifier.fillMaxWidth(0.65f)
        )

        Spacer(modifier = Modifier.height(15.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            val slideSpring = spring<IntOffset>(
                stiffness = AnimationConfig.STIFFNESS,
                dampingRatio = AnimationConfig.DAMPING
            )

            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally(animationSpec = slideSpring) { it } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = slideSpring) { -it } + fadeOut()
                    } else {
                        slideInHorizontally(animationSpec = slideSpring) { -it } + fadeIn() togetherWith
                                slideOutHorizontally(animationSpec = slideSpring) { it } + fadeOut()
                    }
                },
                label = "DocsTabTransition"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> DocsContent()
                    1 -> ReleasesContent()
                }
            }
        }
    }
}

@Composable
fun DocsContent() {
    UnderConstructionView()
}

// --- RELEASES TAB IMPLEMENTATION ---

data class ReleaseData(
    val version: String,
    val title: String,
    val tag: String,
    val categories: List<ReleaseCategory>
)

data class ReleaseCategory(
    val title: String,
    val points: List<String>
)

@Composable
fun ReleasesContent() {
    val releases = listOf(
        // --- v0.2 (NEWEST) ---
        ReleaseData(
            version = "v0.2",
            title = "The Custom Workbench",
            tag = "Current PR Build",
            categories = listOf(
                ReleaseCategory(
                    "User Interface",
                    listOf(
                        "Custom Encryption Tab: Full manual control over encryption layers.",
                        "Interactive Physics: Implemented spring-based pill buttons and transitions.",
                        "Movable Layers: Reorder algorithms using Up/Down arrows with smooth animations.",
                        "Polished Lists: Added fading edges and custom animated scrollbars for long lists.",
                        "Search: Added filterable algorithm list with descriptions."
                    )
                ),
                ReleaseCategory(
                    "Core Cryptography (Engine v0.8.0)",
                    listOf(
                        "Engine Fixes: Corrected Block Size and Key Size logic for algorithms like Blowfish and RC6.",
                        "Compression: Added ZLIB compression toggle (Compression-before-Encryption).",
                        "Expanded Registry: Added Camellia, SM4, GOST, CAST6, and more.",
                        "Binary Packing: Optimized the internal container format for variable block sizes."
                    )
                ),
                ReleaseCategory(
                    "Known Limitations",
                    listOf(
                        "Side Modules: All tabs except Home & Release are placeholders.",
                        "Lackluster optimization: The encryption/decryption, as well as the animations lack proper optimizations."
                    )
                )

            )
        ),
        // --- v0.1 (FOUNDATION) ---
        ReleaseData(
            version = "v0.1",
            title = "The Foundation",
            tag = "Initial Pre-release",
            categories = listOf(
                ReleaseCategory(
                    "Core Cryptography (Engine v0.7.0)",
                    listOf(
                        "Initial Encryption implementation: Randomized triple-layer chain (AES->Twofish->Serpent).",
                        "Blob Container: Opaque Base64 output with hidden metadata.",
                        "HMAC Integrity: Encrypt-then-MAC architecture using HKDF-derived keys.",
                        "Memory Hardening: Argon2id (64MB) + SHA-512 pre-hashing."
                    )
                ),
                ReleaseCategory(
                    "User Interface",
                    listOf(
                        "Material 3 Design: Initial UI with dynamic theming.",
                        "System Console: Dedicated Logs window for auditing steps.",
                        "Navigation Drawer: Skeleton structure implemented."
                    )
                ),
                ReleaseCategory(
                    "Known Limitations",
                    listOf(
                        "Side Modules: All tabs except Home/Auto are placeholders.",
                    )
                )
            )
        )
    )

    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(releases) { index, release ->
            ReleaseCard(release, defaultExpanded = false)
        }
    }
}
@Composable
fun ReleaseCard(release: ReleaseData, defaultExpanded: Boolean) {
    var expanded by remember { mutableStateOf(defaultExpanded) }

    // Interaction Source to drive the "Squish" animation
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Physics: Squish animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "CardBounce"
    )

    val shape = RoundedCornerShape(12.dp)

    // Manual Box Stack
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale) // 1. Apply Physics Scale
            .clip(shape)  // 2. Clip shape
            .background(MaterialTheme.colorScheme.surface) // 3. Background
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) // 4. Border
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { expanded = !expanded }
            )
            .padding(16.dp) // 5. Internal Padding
            .animateContentSize() // 6. Expand/Collapse Animation
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = release.version,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = release.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = release.tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Collapsible Content
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                release.categories.forEach { category ->
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    category.points.forEach { point ->
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text(
                                text = "• ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}