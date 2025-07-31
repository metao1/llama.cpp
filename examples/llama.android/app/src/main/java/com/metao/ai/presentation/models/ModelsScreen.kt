package com.metao.ai.presentation.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metao.ai.domain.model.DownloadState
import com.metao.ai.domain.model.ModelInfo
import com.metao.ai.domain.model.ModelLoadState
import org.koin.androidx.compose.koinViewModel
import kotlin.math.min

@Composable
fun ModelsScreen(
    modifier: Modifier = Modifier,
    viewModel: ModelsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val maxWidth = min(configuration.screenWidthDp.dp.value, 500f).dp // Increased drawer size

    var showAddModelDialog by remember { mutableStateOf(false) }

    // Load models when screen is first displayed
    LaunchedEffect(Unit) {
        if (uiState.models.isEmpty() && !uiState.isLoading) {
            viewModel.loadModels()
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            Text(
                text = "Available Models",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.models) { model ->
                        ModelCard(
                            model = model,
                            downloadState = viewModel.getDownloadState(model.id),
                            loadState = viewModel.getLoadState(model.id),
                            isLoaded = viewModel.isModelLoaded(model.id),
                            onDownload = { viewModel.downloadModel(model) },
                            onLoad = { viewModel.loadModel(model) }
                        )
                    }
                }
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                ) {
                    Text(
                        text = error,
                        color = Color.Red,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Floating Action Button to add custom models
        FloatingActionButton(
            onClick = { showAddModelDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Model"
            )
        }

        // Add Model Dialog
        AddModelDialog(
            isVisible = showAddModelDialog,
            onDismiss = { showAddModelDialog = false },
            onAddModel = { modelData ->
                viewModel.addCustomModel(modelData)
            }
        )
    }
}

@Composable
private fun ModelCard(
    model: ModelInfo,
    downloadState: DownloadState,
    loadState: ModelLoadState,
    isLoaded: Boolean,
    onDownload: () -> Unit,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = "Size: ${formatFileSize(model.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            when {
                !model.isDownloaded -> {
                    DownloadButton(
                        downloadState = downloadState,
                        onDownload = onDownload
                    )
                }
                isLoaded -> {
                    Text(
                        text = "✓ Loaded",
                        color = Color.Green,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    LoadButton(
                        loadState = loadState,
                        onLoad = onLoad
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadButton(
    downloadState: DownloadState,
    onDownload: () -> Unit
) {
    when (downloadState) {
        is DownloadState.Idle -> {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download")
            }
        }
        is DownloadState.Preparing -> {
            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Preparing...")
            }
        }
        is DownloadState.Downloading -> {
            Column {
                LinearProgressIndicator(
                    progress = downloadState.progress,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Downloading... ${(downloadState.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        is DownloadState.Completed -> {
            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Downloaded")
            }
        }
        is DownloadState.Failed -> {
            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun LoadButton(
    loadState: ModelLoadState,
    onLoad: () -> Unit
) {
    when (loadState) {
        is ModelLoadState.Idle -> {
            Button(
                onClick = onLoad,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load")
            }
        }
        is ModelLoadState.Loading -> {
            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Loading...")
            }
        }
        is ModelLoadState.Loaded -> {
            Button(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Green
                )
            ) {
                Text("✓ Loaded")
            }
        }
        is ModelLoadState.Failed -> {
            Button(
                onClick = onLoad,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Retry Load")
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }
}
