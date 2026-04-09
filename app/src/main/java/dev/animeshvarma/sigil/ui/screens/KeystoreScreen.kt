@file:Suppress("AssignedValueIsNeverRead")

package dev.animeshvarma.sigil.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.data.VaultEntry
import dev.animeshvarma.sigil.util.SecureAlertDialog
import dev.animeshvarma.sigil.ui.components.StyledLayerContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KeystoreScreen(viewModel: SigilViewModel) {
    val entries by viewModel.vaultEntries.collectAsState()

    KeystoreContent(
        entries = entries,
        onDelete = { viewModel.deleteFromVault(it) },
        onRename = { old, new -> viewModel.renameVaultEntry(old, new) },
        onView = { alias, callback -> viewModel.viewKey(alias, callback) },
        // Use Secure Clipboard method
        onCopy = { key -> viewModel.copyToClipboardSecurely(key, "Sigil Key") }
    )
}

@Composable
fun KeystoreContent(
    entries: List<VaultEntry>,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onView: (String, (String?) -> Unit) -> Unit,
    onCopy: (String) -> Unit
) {
    var entryToDelete by remember { mutableStateOf<VaultEntry?>(null) }
    var entryToRename by remember { mutableStateOf<VaultEntry?>(null) }
    var entryToWarn by remember { mutableStateOf<VaultEntry?>(null) }
    var entryToView by remember { mutableStateOf<VaultEntry?>(null) }

    var renameText by remember { mutableStateOf("") }
    var revealedKey by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // TEE Header
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Hardware Keystore", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Keys encrypted by Android TEE.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.KeyOff, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Vault is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(entries) { entry ->
                    VaultItem(
                        entry = entry,
                        onDelete = { entryToDelete = entry },
                        onRename = {
                            renameText = entry.alias
                            entryToRename = entry
                        },
                        onView = { entryToWarn = entry }
                    )
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. DELETE CONFIRMATION
    if (entryToDelete != null) {
        SecureAlertDialog(
            onDismissRequest = { entryToDelete = null },
            icon = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Key?") },
            text = { Text("This action cannot be undone. Any data encrypted with '${entryToDelete?.alias}' will be permanently inaccessible.") },
            confirmButton = {
                Button(
                    onClick = {
                        entryToDelete?.let { onDelete(it.alias) }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { entryToDelete = null }) { Text("Cancel") } }
        )
    }

    // 2. RENAME DIALOG
    if (entryToRename != null) {
        SecureAlertDialog(
            onDismissRequest = { entryToRename = null },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text("Rename Key") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (renameText.isNotBlank() && entryToRename != null) {
                        onRename(entryToRename!!.alias, renameText)
                        entryToRename = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { entryToRename = null }) { Text("Cancel") } }
        )
    }

    // 3. SECURITY WARNING (Shoulder Surfing)
    if (entryToWarn != null) {
        SecureAlertDialog(
            onDismissRequest = { entryToWarn = null },
            icon = { Icon(Icons.Default.Visibility, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Reveal Secret Key?") },
            text = { Text("The raw password/key will be displayed on screen. Ensure no one is watching.") },
            confirmButton = {
                Button(onClick = {
                    val entry = entryToWarn!!
                    entryToWarn = null
                    onView(entry.alias) { key ->
                        revealedKey = key ?: "Decryption Error"
                        entryToView = entry
                    }
                }) { Text("Reveal") }
            },
            dismissButton = { TextButton(onClick = { entryToWarn = null }) { Text("Cancel") } }
        )
    }

    // 4. VIEW KEY
    if (entryToView != null) {
        SecureAlertDialog(
            onDismissRequest = { entryToView = null; revealedKey = "" },
            title = { Text(entryToView?.alias ?: "Key") },
            text = {
                Column {
                    Text(
                        text = revealedKey,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                            .fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap Copy to use safely.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCopy(revealedKey)
                    // Optional: Close dialog on copy to minimize exposure
                    // entryToView = null
                }) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Secure Copy")
                }
            },
            dismissButton = { TextButton(onClick = { entryToView = null; revealedKey = "" }) { Text("Close") } }
        )
    }
}

@Composable
fun VaultItem(
    entry: VaultEntry,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onView: () -> Unit
) {
    val dateStr = remember(entry.timestamp) {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(entry.timestamp))
    }

    val strengthColor = when {
        entry.strengthScore >= 95 -> Color(0xFF00E676) // Unbreakable
        entry.strengthScore >= 75 -> Color(0xFF81C784) // Strong
        entry.strengthScore >= 40 -> Color(0xFFFFD54F) // Moderate
        else -> Color(0xFFCF6679)                      // Weak
    }

    val strengthFraction = (entry.strengthScore / 100f).coerceIn(0.1f, 1f)

    StyledLayerContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.alias, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))

                // Strength Bar
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .height(4.dp)
                            .width(60.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(strengthFraction)
                                .background(strengthColor, RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(entry.strengthLabel, fontSize = 11.sp, color = strengthColor, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(2.dp))
                Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Action Buttons
            IconButton(onClick = onView) { Icon(Icons.Default.Visibility, "View", tint = MaterialTheme.colorScheme.primary) }
            IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}