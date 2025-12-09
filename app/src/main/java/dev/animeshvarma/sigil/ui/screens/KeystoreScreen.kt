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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.data.VaultEntry
import dev.animeshvarma.sigil.ui.components.StyledLayerContainer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- STATEFUL HOST ---
@Composable
fun KeystoreScreen(viewModel: SigilViewModel) {
    val entries by viewModel.vaultEntries.collectAsState()

    KeystoreContent(
        entries = entries,
        onDelete = { viewModel.deleteFromVault(it) },
        onRename = { old, new -> viewModel.renameVaultEntry(old, new) },
        onView = { alias, callback -> viewModel.viewKey(alias, callback) },
        onLog = { viewModel.addLog(it) }
    )
}

// --- STATELESS UI ---
@Composable
fun KeystoreContent(
    entries: List<VaultEntry>,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onView: (String, (String?) -> Unit) -> Unit,
    onLog: (String) -> Unit
) {
    var entryToDelete by remember { mutableStateOf<VaultEntry?>(null) }
    var entryToRename by remember { mutableStateOf<VaultEntry?>(null) }
    var entryToWarn by remember { mutableStateOf<VaultEntry?>(null) }
    var entryToView by remember { mutableStateOf<VaultEntry?>(null) }
    var renameText by remember { mutableStateOf("") }
    var revealedKey by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        onRename = { renameText = entry.alias; entryToRename = entry },
                        onView = { entryToWarn = entry }
                    )
                }
            }
        }
    }

    // ... [Dialog implementations (Delete, Rename, Warn, View) remain identical to previous] ...
    // (Omitted for brevity, paste from previous V3 KeystoreScreen logic)
    // Ensure you keep the Dialogs here!
}

@Composable
fun VaultItem(entry: VaultEntry, onDelete: () -> Unit, onRename: () -> Unit, onView: () -> Unit) {
    val dateStr = remember(entry.timestamp) { SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(entry.timestamp)) }

    // Entropy Logic
    val strengthColor = when(entry.strengthLabel) {
        "Paranoid" -> Color(0xFF00E676)
        "Strong" -> Color(0xFF81C784)
        "Weak" -> Color(0xFFCF6679)
        else -> Color(0xFFFFD54F) // Moderate
    }
    val strengthFraction = (entry.strengthScore / 100f).coerceIn(0.1f, 1f)

    StyledLayerContainer {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp).animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.alias, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.height(4.dp).width(60.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(strengthFraction).background(strengthColor, RoundedCornerShape(2.dp)))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(entry.strengthLabel, fontSize = 10.sp, color = strengthColor)
                    Spacer(Modifier.width(8.dp))
                    Text(dateStr, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onView, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Visibility, "View", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onRename, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Edit, "Rename", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
        }
    }
}