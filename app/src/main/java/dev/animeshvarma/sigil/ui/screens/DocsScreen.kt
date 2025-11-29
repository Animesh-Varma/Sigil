package dev.animeshvarma.sigil.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.animeshvarma.sigil.ui.components.UnderConstructionView
import dev.animeshvarma.sigil.ui.theme.AnimationConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsScreen() {
    // Local state for this screen's tabs
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Docs", "Releases")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // [FIX] Material 3 Segmented Button (Same size/style as Home)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(38.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                SegmentedButton(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size)
                ) {
                    Text(title)
                }
            }
        }

        Spacer(modifier = Modifier.height(15.dp))

        // [FIX] Animated Content with Spring Physics
        Box(modifier = Modifier.fillMaxSize()) {
            val slideSpring = spring<IntOffset>(
                stiffness = AnimationConfig.STIFFNESS,
                dampingRatio = AnimationConfig.DAMPING
            )

            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    // Logic: If index increases (Docs -> Releases), slide left. Else slide right.
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
    // Placeholder for Docs
    UnderConstructionView()
}

@Composable
fun ReleasesContent() {
    // Placeholder for Releases
    UnderConstructionView()
}