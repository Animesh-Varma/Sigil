package dev.animeshvarma.sigil.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.ui.components.SecurePasswordInput
import dev.animeshvarma.sigil.ui.components.SigilButtonGroup

@Composable
fun EncryptionInterface(viewModel: SigilViewModel, uiState: UiState) {
    val context = LocalContext.current
    val vaultEntries by viewModel.vaultEntries.collectAsState()

    Column(modifier = Modifier.fillMaxHeight()) {
        // 1. Input Field
        OutlinedTextField(
            value = uiState.autoInput,
            onValueChange = { viewModel.onInputTextChanged(it) },
            label = { Text("Text to encrypt/decrypt") },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            )
        )

        Spacer(modifier = Modifier.height(11.dp))

        // 2. SECURE PASSWORD FIELD (Vault Integrated)
        SecurePasswordInput(
            value = uiState.autoPassword,
            onValueChange = { viewModel.onPasswordChanged(it) },
            onSaveRequested = { name ->
                viewModel.saveToVault(alias = name, password = uiState.autoPassword)
            },
            vaultEntries = vaultEntries,
            onEntrySelected = { viewModel.loadFromVault(it) },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            forceDropdownExpanded = uiState.isDemoDropdownExpanded
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Button Group
        SigilButtonGroup(
            onLogs = { viewModel.onLogsClicked() },
            onEncrypt = { viewModel.onEncrypt() },
            onDecrypt = { viewModel.onDecrypt() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Output Field (With Share Intent)
        OutlinedTextField(
            value = uiState.autoOutput,
            onValueChange = { },
            label = { Text("Output") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().height(144.dp),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            trailingIcon = {
                Column(
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    IconButton(onClick = {
                        if (uiState.autoOutput.isNotEmpty()) {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, uiState.autoOutput)
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Share Encrypted Message")
                            context.startActivity(shareIntent)
                            viewModel.addLog("Share Sheet opened.")
                        }
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    IconButton(onClick = {
                        if (uiState.autoOutput.isNotEmpty()) {
                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                            val clip = android.content.ClipData.newPlainText("Sigil Output", uiState.autoOutput)
                            clipboard.setPrimaryClip(clip)
                            viewModel.addLog("Copied to clipboard")
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}