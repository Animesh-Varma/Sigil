package dev.animeshvarma.sigil

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(viewModel: SigilViewModel = androidx.lifecycle.viewmodel.compose.viewModel(), modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()

    // The root container matches the HTML's "flex flex-col"
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Matches bg-background-light/dark
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        // --- 1. HEADER (Matches HTML <header>) ---
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Menu Button (Left)
            IconButton(
                onClick = { /* Handle Menu */ },
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Title + Underline (Center)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sigil",
                    fontSize = 20.sp, // text-xl
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                // The custom underline pill
                Box(
                    modifier = Modifier
                        .width(32.dp) // w-8
                        .height(2.dp) // h-0.5
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- 2. SEGMENTED CONTROL (Matches HTML "border border-outline-dark rounded-full") ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = CircleShape
                )
                .clip(CircleShape)
                .padding(4.dp) // p-1 in HTML
        ) {
            SigilMode.values().forEachIndexed { index, mode ->
                val isSelected = uiState.selectedMode == mode
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                        )
                        .clickable { viewModel.onModeSelected(mode) }
                ) {
                    Text(
                        text = mode.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- 3. INPUT AREA ---
        Column(
            modifier = Modifier
                .weight(1f) // flex-grow
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Text Input (Textarea)
            // Custom OutlinedTextField to match "bg-transparent" look
            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = { viewModel.onInputTextChanged(it) },
                label = { Text("Text to encrypt/decrypt") },
                modifier = Modifier
                    .weight(1f) // flex-grow
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp), // rounded-lg
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            )

            // Password Input
            OutlinedTextField(
                value = uiState.password,
                onValueChange = { viewModel.onPasswordChanged(it) },
                label = { Text("Password") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp), // h-14 approx
                shape = RoundedCornerShape(12.dp),
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                )
            )

            // --- 4. BUTTONS ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // LOGS BUTTON (Outlined)
                OutlinedButton(
                    onClick = { viewModel.onLogsClicked() },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Logs", color = MaterialTheme.colorScheme.primary)
                }

                // ACTION BUTTONS GROUP
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Encrypt
                    Button(
                        onClick = { viewModel.onEncrypt() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Encrypt")
                    }

                    // Decrypt
                    Button(
                        onClick = { viewModel.onDecrypt() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Text("Decrypt")
                    }
                }
            }

            // --- 5. OUTPUT AREA ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(144.dp) // h-36
            ) {
                OutlinedTextField(
                    value = uiState.outputText,
                    onValueChange = { /* Read-only, do nothing */ },
                    label = { Text("Output") },
                    readOnly = true,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                )

                // Copy Icon (Absolute top right)
                IconButton(
                    onClick = { /* Copy */ },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}