package dev.animeshvarma.sigil.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.animeshvarma.sigil.SigilViewModel
import dev.animeshvarma.sigil.model.UiState

@Composable
fun EncryptionInterface(viewModel: SigilViewModel, uiState: UiState) {
    val clipboardManager = LocalClipboardManager.current
    val spacing = 18.dp

    Column(modifier = Modifier.fillMaxHeight()) {
        // 1. Input Field (Auto State)
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

        // 2. Password Field (Auto State)
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

        Spacer(modifier = Modifier.height(spacing))

        // 3. Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.onLogsClicked() },
                shape = CircleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Logs", color = MaterialTheme.colorScheme.primary)
            }

            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.onEncrypt() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) { Text("Encrypt") }

                Button(
                    onClick = { viewModel.onDecrypt() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) { Text("Decrypt") }
            }
        }

        // This compensates for the visual top padding of the Output TextField label.
        Spacer(modifier = Modifier.height(10.dp))

        // 4. Output Field (Auto State)
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
                        clipboardManager.setText(AnnotatedString(uiState.autoOutput))
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