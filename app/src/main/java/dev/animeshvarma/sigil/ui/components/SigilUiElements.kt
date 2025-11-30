package dev.animeshvarma.sigil.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SigilButtonGroup(
    onLogs: () -> Unit,
    onEncrypt: () -> Unit,
    onDecrypt: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // 1. Logs (Outlined Look - slightly narrower base weight)
        BounceablePill(
            text = "Logs",
            onClick = onLogs,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            baseWeight = 0.65f
        )

        // 2. Encrypt (Primary Look)
        BounceablePill(
            text = "Encrypt",
            onClick = onEncrypt,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            border = null,
            baseWeight = 1f
        )

        // 3. Decrypt (Tonal Look)
        BounceablePill(
            text = "Decrypt",
            onClick = onDecrypt,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            border = null,
            baseWeight = 1f
        )
    }
}

@Composable
private fun RowScope.BounceablePill(
    text: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    border: BorderStroke?,
    baseWeight: Float,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val targetWeight = if (isPressed) baseWeight * 1.15f else baseWeight

    val weight by animateFloatAsState(
        targetValue = targetWeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "PillExpansion"
    )

    Surface(
        modifier = modifier
            .weight(weight)
            .fillMaxHeight(),
        shape = CircleShape,
        color = containerColor,
        border = border,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SigilSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onItemSelection: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.height(36.dp)
    ) {
        items.forEachIndexed { index, label ->
            SegmentedButton(
                selected = index == selectedIndex,
                onClick = { onItemSelection(index) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = items.size)
            ) {
                Text(label)
            }
        }
    }
}

@Composable
fun StyledLayerContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content
    )
}