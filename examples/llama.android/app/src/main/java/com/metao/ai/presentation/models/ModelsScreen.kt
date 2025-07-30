package com.metao.ai.presentation.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val maxWidth = min(configuration.screenWidthDp.dp.value, 400f).dp

    // Load models when screen is first displayed
    LaunchedEffect(Unit) {
        if (uiState.models.isEmpty() && !uiState.isLoading) {
            viewModel.loadModels()
        }
    }

    Column(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Text(
            text = "Models",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
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
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = model.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Text(
                text = model.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(vertical = 14.dp)
            )

            Text(
                text = "Size: ${formatBytes(model.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    contentColor = Color.White
                )
            ) {
                Text("Download")
            }
        }
        is DownloadState.Preparing -> {
            Button(
                onClick = { },
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.White
                )
            ) {
                Text("Preparing...")
            }
        }
        is DownloadState.Downloading -> {
            Column {
                Button(
                    onClick = { },
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = Color.Gray,
                        disabledContentColor = Color.White
                    )
                ) {
                    Text("Downloading ${(downloadState.progress * 100).toInt()}%")
                }
                LinearProgressIndicator(
                    progress = downloadState.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
        is DownloadState.Failed -> {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text("Retry")
            }
        }
        is DownloadState.Completed -> {
            Text(
                text = "✓ Downloaded",
                color = Color.Green,
                fontWeight = FontWeight.Bold
            )
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
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Blue,
                    contentColor = Color.White
                )
            ) {
                Text("Load")
            }
        }
        is ModelLoadState.Loading -> {
            Button(
                onClick = { },
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.White
                )
            ) {
                Text("Loading...")
            }
        }
        is ModelLoadState.Failed -> {
            Button(
                onClick = onLoad,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red,
                    contentColor = Color.White
                )
            ) {
                Text("Retry Load")
            }
        }
        is ModelLoadState.Loaded -> {
            Text(
                text = "✓ Loaded",
                color = Color.Green,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }
}
