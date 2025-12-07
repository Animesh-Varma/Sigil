package dev.animeshvarma.sigil.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VisibilityOff
import dev.animeshvarma.sigil.model.AppScreen

@Composable
fun SigilDrawerContent(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit
) {
    var isSpecializedExpanded by remember { mutableStateOf(false) }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 16.dp)
        ) {
            Text(
                text = "Sigil",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            DrawerItem(
                label = "Home",
                icon = Icons.Default.Home,
                isSelected = currentScreen == AppScreen.HOME,
                onClick = { onScreenSelected(AppScreen.HOME) }
            )

            DrawerItem(
                label = "Specialized",
                icon = Icons.Default.EnhancedEncryption,
                isSelected = false,
                onClick = { isSpecializedExpanded = !isSpecializedExpanded },
                trailingIcon = {
                    Icon(
                        imageVector = if (isSpecializedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            )

            AnimatedVisibility(visible = isSpecializedExpanded) {
                Column(modifier = Modifier.padding(start = 24.dp)) {
                    DrawerItem(
                        label = "Steganography",
                        icon = Icons.Default.VisibilityOff,
                        isSelected = currentScreen == AppScreen.STEGANOGRAPHY,
                        onClick = { onScreenSelected(AppScreen.STEGANOGRAPHY) }
                    )

                    DrawerItem(
                        label = "File/Dir Encryption",
                        icon = Icons.Default.Folder,
                        isSelected = currentScreen == AppScreen.FILE_ENCRYPTION,
                        onClick = { onScreenSelected(AppScreen.FILE_ENCRYPTION) }
                    )

                    DrawerItem(
                        label = "Headerless Mode",
                        icon = Icons.Default.Code,
                        isSelected = currentScreen == AppScreen.HEADERLESS,
                        onClick = { onScreenSelected(AppScreen.HEADERLESS) }
                    )

                    DrawerItem(
                        label = "Asymmetric",
                        icon = Icons.Default.Public,
                        isSelected = currentScreen == AppScreen.ASYMMETRIC,
                        onClick = { onScreenSelected(AppScreen.ASYMMETRIC) }
                    )

                    DrawerItem(
                        label = "Partitions",
                        icon = Icons.Default.Texture,
                        isSelected = currentScreen == AppScreen.PARTITIONS,
                        onClick = { onScreenSelected(AppScreen.PARTITIONS) }
                    )
                }
            }

            DrawerItem(
                label = "Keystore",
                icon = Icons.Default.Key,
                isSelected = currentScreen == AppScreen.KEYSTORE,
                onClick = { onScreenSelected(AppScreen.KEYSTORE) },
                containerColor = if (currentScreen == AppScreen.KEYSTORE) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                contentColor = if (currentScreen == AppScreen.KEYSTORE) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
        )

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            DrawerItem(
                label = "Donate",
                icon = Icons.Default.VolunteerActivism,
                isSelected = currentScreen == AppScreen.DONATE,
                onClick = { onScreenSelected(AppScreen.DONATE) }
            )
            DrawerItem(
                label = "Docs/Release Notes",
                icon = Icons.AutoMirrored.Filled.Article,
                isSelected = currentScreen == AppScreen.DOCS,
                onClick = { onScreenSelected(AppScreen.DOCS) }
            )
            DrawerItem(
                label = "Settings",
                icon = Icons.Default.Settings,
                isSelected = currentScreen == AppScreen.SETTINGS,
                onClick = { onScreenSelected(AppScreen.SETTINGS) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun DrawerItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    trailingIcon: @Composable (() -> Unit)? = null,
    containerColor: Color? = null,
    contentColor: Color? = null
) {
    val background = containerColor ?: if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
    val textColor = contentColor ?: if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = contentColor ?: if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick), // Removed bounceClick
        color = background
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor,
                modifier = Modifier.weight(1f)
            )
            trailingIcon?.invoke()
        }
    }
}