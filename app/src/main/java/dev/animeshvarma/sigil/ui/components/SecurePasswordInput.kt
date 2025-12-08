package dev.animeshvarma.sigil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.data.VaultEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SecurePasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSaveRequested: () -> Unit,
    vaultEntries: List<VaultEntry>,
    onEntrySelected: (VaultEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Password / Key") },
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(24.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            trailingIcon = {
                Row {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Visibility"
                        )
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Key, "Vault", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.width(280.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            // Header
            Text(
                "Sigil Vault",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider()

            // Save Option
            DropdownMenuItem(
                text = { Text("Save Current Key") },
                leadingIcon = { Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)) },
                onClick = {
                    onSaveRequested()
                    showMenu = false
                }
            )

            HorizontalDivider()

            // Key List
            if (vaultEntries.isEmpty()) {
                Text(
                    "No saved keys found.",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                vaultEntries.forEach { entry ->
                    val color = when(entry.strengthLabel) {
                        "Weak" -> Color(0xFFCF6679)
                        "Strong" -> Color(0xFF81C784)
                        "Paranoid" -> Color(0xFF00E676)
                        else -> Color(0xFFFFD54F) // Moderate
                    }

                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(entry.alias, fontWeight = FontWeight.Medium)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(6.dp).background(color, androidx.compose.foundation.shape.CircleShape))
                                    Spacer(Modifier.width(4.dp))
                                    Text(entry.strengthLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        onClick = {
                            onEntrySelected(entry)
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}