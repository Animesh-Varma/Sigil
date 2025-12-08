package dev.animeshvarma.sigil.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.data.VaultEntry
import dev.animeshvarma.sigil.ui.components.StyledLayerContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun KeystoreScreen(viewModel: SigilViewModel) {
    val entries by viewModel.vaultEntries.collectAsState()

    // State for Dialogs
    var entryToDelete by remember { mutableStateOf<VaultEntry?>(null) }
    var entryToRename by remember { mutableStateOf<VaultEntry?>(null) }
    var entryToView by remember { mutableStateOf<VaultEntry?>(null) }

    // Temp variables
    var renameText by remember { mutableStateOf("") }
    var revealedKey by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Security, null, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Hardware Keystore", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Keys stored in TEE Enclave.", fontSize = 12.sp)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Vault is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries) { entry ->
                    VaultItem(
                        entry = entry,
                        onDelete = { entryToDelete = entry },
                        onRename = {
                            renameText = entry.alias
                            entryToRename = entry
                        },
                        onView = {
                            viewModel.viewKey(entry.alias) { key ->
                                revealedKey = key ?: "Error"
                                entryToView = entry
                            }
                        }
                    )
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. DELETE CONFIRMATION
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Key?") },
            text = { Text("Are you sure you want to destroy '${entryToDelete?.alias}'? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryToDelete?.let { viewModel.deleteFromVault(it.alias) }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // 2. RENAME DIALOG
    if (entryToRename != null) {
        AlertDialog(
            onDismissRequest = { entryToRename = null },
            title = { Text("Rename Key") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank() && entryToRename != null) {
                            viewModel.renameVaultEntry(entryToRename!!.alias, renameText)
                            entryToRename = null
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { entryToRename = null }) { Text("Cancel") }
            }
        )
    }

    // 3. VIEW KEY DIALOG
    if (entryToView != null) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = {
                entryToView = null
                revealedKey = ""
            },
            title = { Text("Key: ${entryToView?.alias}") },
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
                    Text("Warning: Ensure no one is looking.", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(revealedKey))
                    viewModel.addLog("Key copied to clipboard.")
                }) { Text("Copy") }
            },
            dismissButton = {
                TextButton(onClick = {
                    entryToView = null
                    revealedKey = ""
                }) { Text("Close") }
            }
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
        SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(entry.timestamp))
    }

    val strengthColor = when(entry.strengthLabel) {
        "Weak" -> Color(0xFFCF6679)
        "Strong" -> Color(0xFF81C784)
        "Paranoid" -> Color(0xFF00E676)
        else -> Color(0xFFFFD54F)
    }

    StyledLayerContainer {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.alias, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(dateStr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(entry.strengthLabel, fontSize = 10.sp, color = strengthColor)
                }
            }

            // Action Icons
            IconButton(onClick = onView, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Visibility, "View", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Edit, "Rename", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            }
        }
    }
}