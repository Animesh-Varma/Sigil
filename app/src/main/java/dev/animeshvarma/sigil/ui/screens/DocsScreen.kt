package dev.animeshvarma.sigil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.ui.components.UnderConstructionView

@Composable
fun DocsScreen() {
    // Local state for this screen's tabs
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Docs", "Releases")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tab Selector (Matches Home Style & Width)
        Row(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(36.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clip(CircleShape)
                .padding(2.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { selectedTab = index }
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> DocsContent()
                1 -> ReleasesContent()
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