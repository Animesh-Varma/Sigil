package dev.animeshvarma.sigil.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.data.VaultEntry

@Composable
fun SecurePasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSaveRequested: (String) -> Unit, // Changed to accept Name
    vaultEntries: List<VaultEntry>,
    onEntrySelected: (VaultEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var newKeyName by remember { mutableStateOf("") }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Visibility"
                        )
                    }

                    // Key Icon with Menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Key, "Vault", tint = MaterialTheme.colorScheme.primary)
                        }

                        // POPUP ON THE RIGHT
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = DpOffset(x = 10.dp, y = 0.dp), // Slight offset
                            modifier = Modifier
                                .width(240.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge),
                            shape = MaterialTheme.shapes.extraLarge // M3 Expressive Roundness
                        ) {
                            // Header
                            Text(
                                "Sigil Vault",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Save Option
                            DropdownMenuItem(
                                text = { Text("Save Current Key") },
                                leadingIcon = { Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMenu = false
                                    newKeyName = "" // Reset
                                    showNameDialog = true
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
                                        else -> Color(0xFFFFD54F)
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
            }
        )
    }

    // NAME DIALOG
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Save Key") },
            text = {
                Column {
                    Text("Enter a name for this key:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newKeyName,
                        onValueChange = { newKeyName = it },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newKeyName.isNotBlank()) {
                            onSaveRequested(newKeyName)
                            showNameDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }
}