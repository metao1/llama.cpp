package com.metao.ai.presentation.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun AddModelDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onAddModel: (AddModelDialogData) -> Unit
) {
    if (isVisible) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var sizeGB by remember { mutableStateOf("") }
        
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Add New Model",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Model Name") },
                        placeholder = { Text("e.g., Llama 2 7B Chat") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        placeholder = { Text("Brief description of the model") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                    
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Download URL") },
                        placeholder = { Text("https://huggingface.co/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    OutlinedTextField(
                        value = sizeGB,
                        onValueChange = { sizeGB = it },
                        label = { Text("Size (GB)") },
                        placeholder = { Text("e.g., 4.2") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss
                        ) {
                            Text("Cancel")
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = {
                                if (name.isNotBlank() && url.isNotBlank()) {
                                    val size = sizeGB.toFloatOrNull() ?: 1.0f
                                    onAddModel(
                                        AddModelDialogData(
                                            name = name.trim(),
                                            description = description.trim(),
                                            url = url.trim(),
                                            sizeBytes = (size * 1024 * 1024 * 1024).toLong()
                                        )
                                    )
                                    onDismiss()
                                }
                            },
                            enabled = name.isNotBlank() && url.isNotBlank()
                        ) {
                            Text("Add Model")
                        }
                    }
                }
            }
        }
    }
}
