package dev.animeshvarma.sigil.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.UiState
import dev.animeshvarma.sigil.ui.components.SigilButtonGroup //  Added

@Composable
fun EncryptionInterface(viewModel: SigilViewModel, uiState: UiState) {
    val context = LocalContext.current

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

        // 2. Password Field
        OutlinedTextField(
            value = uiState.autoPassword,
            onValueChange = { viewModel.onPasswordChanged(it) },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(24.dp),
            visualTransformation = PasswordVisualTransformation(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 3. Button Group
        SigilButtonGroup(
            onLogs = { viewModel.onLogsClicked() },
            onEncrypt = { viewModel.onEncrypt() },
            onDecrypt = { viewModel.onDecrypt() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Output Field
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
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}