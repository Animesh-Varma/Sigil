package dev.animeshvarma.sigil.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SigilButtonGroup(
    onLogs: () -> Unit,
    onEncrypt: () -> Unit,
    onDecrypt: () -> Unit,
    modifier: Modifier = Modifier
) {
    // [FIX 1] Standard Material 3 Action Group
    // Distinct buttons with clear hierarchy (Outlined -> Primary -> Tonal)
    Row(
        modifier = modifier.fillMaxWidth().height(48.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp) // Standard spacing
    ) {

        // 1. Logs (Utility Action)
        OutlinedButton(
            onClick = onLogs,
            modifier = Modifier.weight(0.8f).fillMaxHeight(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(24.dp) // Standard pill shape
        ) {
            Text("Logs")
        }

        // 2. Encrypt (Primary Action)
        Button(
            onClick = onEncrypt,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Encrypt")
        }

        // 3. Decrypt (Secondary Action)
        FilledTonalButton(
            onClick = onDecrypt,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            shape = RoundedCornerShape(24.dp)
        ) {
            Text("Decrypt")
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