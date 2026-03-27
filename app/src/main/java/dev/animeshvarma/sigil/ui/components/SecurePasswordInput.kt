@file:Suppress("AssignedValueIsNeverRead")

package dev.animeshvarma.sigil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.data.VaultEntry

/**
 * A composable password/key input with visibility toggle, integrated vault dropdown, and save/overwrite flows.
 *
 * Shows an outlined text field for entering a password or key, a trailing visibility toggle, and a vault menu
 * that lets the user save the current value (with alias entry and duplicate-name handling) or fill the field
 * from existing vault entries. When saving, the composable prompts for an alias and, if the alias already exists
 * (case-insensitive), requests confirmation to overwrite.
 *
 * @param value The current text shown in the input.
 * @param onValueChange Callback invoked when the input text changes.
 * @param onSaveRequested Callback invoked to persist a key under the provided alias.
 * @param vaultEntries List of vault entries displayed in the vault dropdown; entries supply alias, strengthScore, and strengthLabel.
 * @param onEntrySelected Callback invoked when a vault entry is chosen; the selected entry should be applied by the caller.
 * @param modifier Modifier applied to the root composable.
 * @param forceDropdownExpanded When true, forces the vault dropdown to open (useful for guided flows or testing).
 */
@Composable
fun SecurePasswordInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSaveRequested: (String) -> Unit,
    vaultEntries: List<VaultEntry>,
    onEntrySelected: (VaultEntry) -> Unit,
    modifier: Modifier = Modifier,
    forceDropdownExpanded: Boolean = false
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Dialog States
    var showNameDialog by remember { mutableStateOf(false) }
    var showOverwriteDialog by remember { mutableStateOf(false) }
    var newKeyName by remember { mutableStateOf("") }

    val haptic = LocalHapticFeedback.current

    LaunchedEffect(forceDropdownExpanded) {
        if (forceDropdownExpanded) showMenu = true
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Password / Key") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            trailingIcon = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    // 1. Visibility Toggle
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide Password" else "Show Password",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 2. Vault Menu
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Key, "Vault", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // VAULT POPUP
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            offset = DpOffset(x = 10.dp, y = 0.dp),
                            modifier = Modifier
                                .width(260.dp)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            // Header
                            Text(
                                "SIGIL VAULT",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.primary
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Save Option
                            DropdownMenuItem(
                                text = { Text("Save Current Key") },
                                leadingIcon = { Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMenu = false
                                    newKeyName = ""
                                    showNameDialog = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Key List
                            if (vaultEntries.isEmpty()) {
                                Box(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "Vault is empty.",
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                vaultEntries.forEach { entry ->
                                    // Visual Security Indicator (Matches SecureMemory logic)
                                    val strengthColor = when {
                                        entry.strengthScore < 40 -> Color(0xFFCF6679) // Red
                                        entry.strengthScore < 75 -> Color(0xFFFFD54F) // Yellow
                                        entry.strengthScore < 95 -> Color(0xFF81C784) // Green
                                        else -> Color(0xFF00E676)                     // Bright Green
                                    }

                                    DropdownMenuItem(
                                        text = {
                                            Column(verticalArrangement = Arrangement.Center) {
                                                Text(
                                                    text = entry.alias,
                                                    fontWeight = FontWeight.SemiBold,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    // Strength Dot
                                                    Box(
                                                        Modifier
                                                            .size(8.dp)
                                                            .background(strengthColor, CircleShape)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = entry.strengthLabel,
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            // Auto-hide password when filling from vault for "shoulder surfing" protection
                                            passwordVisible = false
                                            onEntrySelected(entry)
                                            showMenu = false
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    // SAVE KEY DIALOG
    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            icon = { Icon(Icons.Default.Save, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Save to Vault") },
            text = {
                Column {
                    Text("Give this encryption key a unique alias:")
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = newKeyName,
                        onValueChange = { newKeyName = it },
                        singleLine = true,
                        placeholder = { Text("Enter a unique name...") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newKeyName.isNotBlank()) {
                            // CHECK FOR DUPLICATES
                            val exists = vaultEntries.any { it.alias.equals(newKeyName, ignoreCase = true) }
                            if (exists) {
                                showNameDialog = false
                                showOverwriteDialog = true
                            } else {
                                onSaveRequested(newKeyName)
                                showNameDialog = false
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // OVERWRITE WARNING DIALOG
    if (showOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showOverwriteDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Key Exists") },
            text = { Text("A key named '$newKeyName' already exists in the Vault.\n\nDo you want to overwrite it?") },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveRequested(newKeyName)
                        showOverwriteDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Overwrite") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showOverwriteDialog = false
                    showNameDialog = true
                }) { Text("Change Name") }
            }
        )
    }
}