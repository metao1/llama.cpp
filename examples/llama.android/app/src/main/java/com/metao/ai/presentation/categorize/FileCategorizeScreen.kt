package com.metao.ai.presentation.categorize

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.model.CategorizationState
import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.MoveOperation
import com.metao.ai.domain.model.MoveReport
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCategorizeScreen(
    modifier: Modifier = Modifier,
    viewModel: FileCategorizeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var autoOpenPicker by remember { mutableStateOf(false) }

    // Periodically check model status
    LaunchedEffect(Unit) {
        viewModel.refreshModelStatus()
        if (uiState.selectedDirectory == null) {
            autoOpenPicker = true
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        // Header
        Text(
            text = "File Categorizer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Directory Selection
        DirectorySelectionCard(
            selectedDirectory = uiState.selectedDirectory,
            onDirectorySelected = viewModel::selectDirectory,
            autoOpen = autoOpenPicker && uiState.selectedDirectory == null,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        // Action Buttons
        ActionButtonsRow(
            uiState = uiState,
            onScanDirectory = viewModel::scanDirectory,
            onScanAllDirectories = viewModel::scanAllDirectories,
            onCategorizeFiles = viewModel::categorizeFiles,
            onSelectAll = viewModel::selectAllMoveOperations,
            onDeselectAll = viewModel::deselectAllMoveOperations,
            onExecuteMove = viewModel::executeMoveOperations,
            onReset = viewModel::resetCategorization,
            modifier = Modifier.padding(bottom = 2.dp),
        )

        // Content based on state
        when {
            uiState.showMovePreview && uiState.moveOperations.isNotEmpty() -> {
                MoveOperationsList(
                    operations = uiState.moveOperations,
                    onToggleOperation = viewModel::toggleMoveOperation,
                    onThresholdChanged = viewModel::updateConfidenceThreshold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            uiState.moveReport != null -> {
                MoveReportDisplay(
                    report = uiState.moveReport!!,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            uiState.categorizationState is CategorizationState.Idle -> {
                if (uiState.scannedFiles.isNotEmpty()) {
                    ScannedFilesList(
                        files = uiState.scannedFiles,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (uiState.selectedDirectory != null) {
                    EmptyStateMessage(
                        "No files found in '${uiState.selectedDirectory}'. Try selecting a different directory or add some files to scan.",
                    )
                } else {
                    EmptyStateMessage("Select a directory and scan for files to get started")
                }
            }

            uiState.categorizationState is CategorizationState.CategorizationComplete -> {
                CategorizationResultsList(
                    results = uiState.categorizationResults,
                    onConfirmResult = viewModel::confirmCategorization,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                // Show progress or other states
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Permission Warning Card
        val context = LocalContext.current
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ Storage Permission Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Android requires 'All Files Access' permission to read files in folders like '${uiState.selectedDirectory ?: "selected directory"}'.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val intent =
                                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                } catch (e2: Exception) {
                                    android.util.Log.e("FileCategorizeScreen", "Failed to open settings", e2)
                                }
                            }
                        },
                    ) {
                        Text("Grant All Files Access")
                    }
                }
            }
        }

        // Status Display
        StatusDisplay(
            state = uiState.categorizationState,
            scannedFilesCount = uiState.scannedFiles.size,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        // Debug info
        Card(modifier = Modifier.padding(bottom = 16.dp)) {
            Column(
                modifier =
                    Modifier
                        .padding(8.dp)
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text("Debug Info:", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Selected: ${uiState.selectedDirectory ?: "None"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "State: ${uiState.categorizationState}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Files: ${uiState.scannedFiles.size}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Model Loaded: ${uiState.isModelLoaded}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.isModelLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )

                if (!uiState.isModelLoaded) {
                    Text(
                        "⚠️ Load a model from the side drawer to enable categorization",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (uiState.scannedFiles.isEmpty() && uiState.selectedDirectory != null) {
                    Text(
                        "💡 Press 'Scan Current' to find files. If 0 files found on Android 11+, use 'Select Directory' above to pick the folder via System Folder Picker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else if (uiState.scannedFiles.isNotEmpty()) {
                    Text(
                        "✅ Ready to categorize ${uiState.scannedFiles.size} files!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            // Create some test files for demonstration
                            uiState.selectedDirectory?.let { dir ->
                                createTestFiles(dir)
                                // Automatically rescan after creating files
                                viewModel.scanDirectory()
                            }
                        },
                        enabled = uiState.selectedDirectory != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Create Test Files")
                    }

                    Button(
                        onClick = viewModel::refreshModelStatus,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Refresh Model Status")
                    }
                }
            }
        }

        // Error Display
        uiState.error?.let { error ->
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        // Debug logging
        LaunchedEffect(uiState) {
            android.util.Log.d(
                "FileCategorizeScreen",
                "UI State updated: selectedDirectory=${uiState.selectedDirectory}, state=${uiState.categorizationState}, scannedFiles=${uiState.scannedFiles.size}, modelLoaded=${uiState.isModelLoaded}",
            )
        }
    }
}

@Composable
private fun DirectorySelectionCard(
    selectedDirectory: String?,
    onDirectorySelected: (String) -> Unit,
    autoOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var showPickerDialog by remember { mutableStateOf(autoOpen) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Selected Directory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedDirectory != null) {
                Text(
                    text = selectedDirectory,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    text = "No directory selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { showPickerDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Directory")
            }
        }
    }

    if (showPickerDialog) {
        DirectoryPickerDialog(
            selectedDirectory = selectedDirectory,
            onDirectorySelected = onDirectorySelected,
            onDismiss = { showPickerDialog = false },
        )
    }
}

@Composable
private fun DirectoryPickerDialog(
    selectedDirectory: String?,
    onDirectorySelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val rootStoragePath = Environment.getExternalStorageDirectory().absolutePath

    var currentPath by remember {
        mutableStateOf(
            if (!selectedDirectory.isNullOrEmpty() && File(selectedDirectory).isDirectory) {
                selectedDirectory
            } else {
                rootStoragePath
            },
        )
    }
    var customPathInput by remember { mutableStateOf(currentPath) }

    val safLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) {
                try {
                    val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: Exception) {
                    android.util.Log.e("DirectoryPicker", "Failed to take persistable URI permission", e)
                }
                val path = getPathFromUri(context, uri) ?: uri.path ?: uri.toString()
                onDirectorySelected(path)
                onDismiss()
            }
        }

    val quickDirs =
        remember {
            listOf(
                "Downloads" to "$rootStoragePath/Download",
                "Documents" to "$rootStoragePath/Documents",
                "Pictures" to "$rootStoragePath/Pictures",
                "DCIM" to "$rootStoragePath/DCIM",
                "Music" to "$rootStoragePath/Music",
                "Movies" to "$rootStoragePath/Movies",
                "Internal Storage" to rootStoragePath,
            ).filter { File(it.second).exists() }
        }

    val subdirectories =
        remember(currentPath) {
            try {
                val dir = File(currentPath)
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()
                        ?.filter { it.isDirectory && !it.name.startsWith(".") && it.canRead() }
                        ?.sortedBy { it.name.lowercase() }
                        ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxWidth(0.92f)
                    .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
            ) {
                Text(
                    text = "Select Preferred Directory",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option 1: Native System File Picker
                Button(
                    onClick = { safLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open System Folder Picker")
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option 2: Quick Access Locations
                Text(
                    text = "Quick Locations",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    quickDirs.forEach { (label, path) ->
                        Button(
                            onClick = {
                                currentPath = path
                                customPathInput = path
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Option 3: Browse Folders
                Text(
                    text = "Browse Folders",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customPathInput,
                    onValueChange = { input ->
                        customPathInput = input
                        if (File(input).isDirectory) {
                            currentPath = input
                        }
                    },
                    label = { Text("Directory Path") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(8.dp),
                    ) {
                        val parentFile = File(currentPath).parentFile
                        if (parentFile != null && parentFile.canRead() && parentFile.absolutePath.startsWith("/storage")) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentPath = parentFile.absolutePath
                                            customPathInput = parentFile.absolutePath
                                        }
                                        .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "📁 .. (Go Up)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }

                        if (subdirectories.isEmpty()) {
                            Text(
                                text = "No subdirectories found in current folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp),
                            )
                        } else {
                            subdirectories.forEach { subDir ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentPath = subDir.absolutePath
                                                customPathInput = subDir.absolutePath
                                            }
                                            .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("📁 ", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = subDir.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            val targetPath = customPathInput.ifBlank { currentPath }
                            onDirectorySelected(targetPath)
                            onDismiss()
                        },
                        enabled = customPathInput.isNotBlank() && File(customPathInput).exists(),
                    ) {
                        Text("Select This Directory")
                    }
                }
            }
        }
    }
}

private fun getPathFromUri(context: android.content.Context, uri: Uri): String? {
    try {
        android.util.Log.d("DirectoryPicker", "Parsing URI from package ${context.packageName}: $uri")
        if (DocumentsContract.isTreeUri(uri)) {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            val type = split[0]
            if (type.equals("primary", ignoreCase = true)) {
                return if (split.size > 1) {
                    "${Environment.getExternalStorageDirectory().absolutePath}/${split[1]}"
                } else {
                    Environment.getExternalStorageDirectory().absolutePath
                }
            } else if (type.startsWith("raw")) {
                return docId.substringAfter("raw:")
            } else if (split.size > 1) {
                val path = "/storage/${split[0]}/${split[1]}"
                if (File(path).exists()) return path
            }
        }

        val docId = try { DocumentsContract.getDocumentId(uri) } catch (_: Exception) { null }
        if (docId != null && docId.contains(":")) {
            val split = docId.split(":")
            if (split[0].equals("primary", ignoreCase = true)) {
                return "${Environment.getExternalStorageDirectory().absolutePath}/${split[1]}"
            }
        }

        val rawPath = uri.path
        if (rawPath != null) {
            if (rawPath.contains("/storage/emulated/0")) {
                return "/storage/emulated/0" + rawPath.substringAfter("/storage/emulated/0")
            }
            if (rawPath.contains("/storage/")) {
                return "/storage/" + rawPath.substringAfter("/storage/")
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("DirectoryPicker", "Error parsing Uri $uri", e)
    }
    return null
}

@Composable
private fun ActionButtonsRow(
    uiState: FileCategorizeUiState,
    onScanDirectory: () -> Unit,
    onScanAllDirectories: () -> Unit,
    onCategorizeFiles: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onExecuteMove: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // First row - Scanning buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    android.util.Log.d("FileCategorizeScreen", "Scan Current button clicked")
                    onScanDirectory()
                },
                enabled = !uiState.selectedDirectory.isNullOrEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan Current")
            }

            Button(
                onClick = onScanAllDirectories,
                enabled = true, // Always enabled for scanning all directories
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan All")
            }
        }

        // Second row - Processing buttons
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onCategorizeFiles,
                enabled =
                    uiState.scannedFiles.isNotEmpty() && uiState.categorizationState == CategorizationState.Idle &&
                        uiState.isModelLoaded,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    when {
                        !uiState.isModelLoaded -> "Load Model First"
                        uiState.scannedFiles.isEmpty() -> "Scan Files First"
                        uiState.categorizationState != CategorizationState.Idle -> "Processing..."
                        else -> "Categorize"
                    },
                )
            }
        }

        // Move operations buttons
        if (uiState.showMovePreview && uiState.moveOperations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onSelectAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select All")
                }

                Button(
                    onClick = onDeselectAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Deselect All")
                }

                Button(
                    onClick = onExecuteMove,
                    enabled = uiState.moveOperations.any { it.isSelected },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Execute Move")
                }
            }
        }

        // Reset button
        if (uiState.categorizationResults.isNotEmpty() || uiState.moveReport != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset")
            }
        }
    }
}

@Composable
private fun StatusDisplay(
    state: CategorizationState,
    scannedFilesCount: Int,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (state) {
                is CategorizationState.Idle -> {
                    Text("Ready", style = MaterialTheme.typography.bodyMedium)
                }

                is CategorizationState.ScanningDirectory -> {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Scanning directories...",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (scannedFilesCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Found $scannedFilesCount files so far...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is CategorizationState.CategorizingFiles -> {
                    Column {
                        Text("Categorizing files...", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Processing: ${state.currentFile}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                is CategorizationState.MovingFiles -> {
                    Column {
                        Text("Moving files...", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = state.progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Moving: ${state.currentFile}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                is CategorizationState.CategorizationComplete -> {
                    Text(
                        "Categorization complete! Review and confirm results below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                is CategorizationState.FilesMovedSuccessfully -> {
                    Text(
                        "Files moved successfully!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                is CategorizationState.Failed -> {
                    Text(
                        "Error: ${state.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScannedFilesList(
    files: List<FileItem>,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Summary header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Scanned Files (${files.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )

                // File type summary
                val fileTypeCounts = files.groupBy { it.fileType }.mapValues { it.value.size }
                Text(
                    text =
                        fileTypeCounts.entries
                            .take(3)
                            .joinToString(", ") { "${it.value} ${it.key.displayName}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            androidx.compose.foundation.lazy.LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 350.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(files.size, key = { index -> files[index].path }) { index ->
                    FileItemCard(
                        fileItem = files[index],
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorizationResultsList(
    results: List<CategorizationResult>,
    onConfirmResult: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Categorization Results (${results.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Column {
                results.forEachIndexed { index, result ->
                    CategorizationResultCard(
                        result = result,
                        onConfirm = { confirmed -> onConfirmResult(index, confirmed) },
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FileItemCard(
    fileItem: FileItem,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = fileItem.fileType.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = fileItem.sizeFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CategorizationResultCard(
    result: CategorizationResult,
    onConfirm: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (result.isConfirmed) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.fileItem.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "→ ${result.suggestedCategory.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "Confidence: ${(result.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (result.reasoning.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Checkbox(
                    checked = result.isConfirmed,
                    onCheckedChange = onConfirm,
                )
            }
        }
    }
}

private fun createTestFiles(directoryPath: String) {
    try {
        val directory = File(directoryPath)
        if (!directory.exists()) directory.mkdirs()

        // Create some test files with different types and names
        val testFiles =
            listOf(
                "receipt_grocery_store.txt" to "Grocery Store Receipt\nDate: 2024-01-15\nTotal: $45.67\nItems: Milk, Bread, Eggs",
                "work_meeting_notes.txt" to
                    "Meeting Notes - Project Planning\nDate: 2024-01-10\nAttendees: John, Sarah, Mike\nAction items: Review budget, Schedule follow-up",
                "passport_copy.txt" to "Passport Information\nDocument Type: Passport\nIssue Date: 2020-05-15\nExpiry Date: 2030-05-15",
                "vacation_photos_list.txt" to "Vacation Photos\nLocation: Hawaii\nDate: Summer 2023\nPhotos: Beach, Sunset, Hiking",
                "software_installer.txt" to "Downloaded Software\nFile: setup.exe\nVersion: 2.1.4\nSize: 150MB",
                "music_playlist.txt" to "My Favorite Songs\nGenre: Rock\nArtist: Various\nTotal: 25 songs",
                "old_temp_file.txt" to "Temporary file\nCreated: 2023-01-01\nStatus: Can be deleted",
            )

        testFiles.forEach { (filename, content) ->
            val file = File(directory, filename)
            if (!file.exists()) {
                file.writeText(content)
            }
        }

        android.util.Log.d(
            "FileCategorizeScreen",
            "Created ${testFiles.size} test files in $directoryPath",
        )
    } catch (e: Exception) {
        android.util.Log.e("FileCategorizeScreen", "Error creating test files", e)
    }
}

@Composable
private fun MoveOperationsList(
    operations: List<MoveOperation>,
    onToggleOperation: (Int) -> Unit,
    onThresholdChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }
    var sliderValue by remember { mutableStateOf(0.5f) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with summary and expand/collapse
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Move Operations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    val selectedCount = operations.count { it.isSelected }
                    Text(
                        text = "$selectedCount/${operations.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))

                // Confidence threshold slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Min Confidence Threshold:",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = "${(sliderValue * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    androidx.compose.material3.Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            sliderValue = newValue
                            onThresholdChanged(newValue)
                        },
                        valueRange = 0f..1f,
                        steps = 10,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val groupedOperations = operations.groupBy { it.categoryName }

                androidx.compose.foundation.lazy.LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                min = 150.dp,
                                max = 450.dp,
                            ),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    groupedOperations.forEach { (categoryName, categoryOperations) ->
                        item(key = "header_$categoryName") {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = categoryName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        text = "${categoryOperations.count { it.isSelected }}/${categoryOperations.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }
                        }

                        items(categoryOperations.size, key = { index -> "${categoryName}_${categoryOperations[index].fromPath}" }) { index ->
                            val operation = categoryOperations[index]
                            val globalIndex = operations.indexOf(operation)

                            MoveOperationCard(
                                operation = operation,
                                onToggle = { onToggleOperation(globalIndex) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } // Close if (isExpanded)
        }
    }
}

@Composable
private fun MoveOperationCard(
    operation: MoveOperation,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (operation.isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.fileItem.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "→ ${operation.categoryName}/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Confidence: ${(operation.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (operation.confidence >= 0.7f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = operation.fileItem.sizeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (operation.reasoning.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = operation.reasoning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Checkbox(
                checked = operation.isSelected,
                onCheckedChange = { onToggle() },
            )
        }
    }
}

@Composable
private fun MoveReportDisplay(
    report: MoveReport,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Move Operation Report",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // Summary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ReportStatCard(
                    title = "Total",
                    value = report.totalOperations.toString(),
                    color = MaterialTheme.colorScheme.primary,
                )
                ReportStatCard(
                    title = "Success",
                    value = report.successfulMoves.toString(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
                ReportStatCard(
                    title = "Failed",
                    value = report.failedMoves.toString(),
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Duration
            Text(
                text = "Duration: ${report.duration}ms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Created directories
            if (report.createdDirectories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Created Directories:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                report.createdDirectories.forEach { dir ->
                    Text(
                        text = "• $dir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // Errors
            if (report.errors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Errors:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                )
                Column(modifier = Modifier.heightIn(max = 150.dp)) {
                    report.errors.forEach { error ->
                        Text(
                            text = "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportStatCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}
