package dev.animeshvarma.sigil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.ui.components.UnderConstructionView

@Composable
fun SettingsScreen(viewModel: SigilViewModel) {
    var triggerOnNextStart by remember { mutableStateOf(viewModel.isOnboardingReset()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "General",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )

        ListItem(
            headlineContent = { Text("Show Onboarding", fontWeight = FontWeight.Medium) },
            supportingContent = { Text("Trigger the introductory tour on the next app launch.") },
            trailingContent = {
                Checkbox(
                    checked = triggerOnNextStart,
                    onCheckedChange = { checked ->
                        triggerOnNextStart = checked
                        viewModel.setOnboardingReset(checked)
                    }
                )
            },
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable {
                    val newState = !triggerOnNextStart
                    triggerOnNextStart = newState
                    viewModel.setOnboardingReset(newState)
                },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            UnderConstructionView()
        }
    }
}