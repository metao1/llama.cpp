package com.metao.ai.presentation.categorize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metao.ai.domain.model.CategorizationState
import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.model.MoveOperation
import com.metao.ai.domain.model.MoveReport
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileCategorizeScreen(
    modifier: Modifier = Modifier, viewModel: FileCategorizeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Periodically check model status
    LaunchedEffect(Unit) {
        viewModel.refreshModelStatus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "File Categorizer",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Directory Selection
        DirectorySelectionCard(
            selectedDirectory = uiState.selectedDirectory,
            onDirectorySelected = viewModel::selectDirectory,
            modifier = Modifier.padding(bottom = 16.dp)
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
            modifier = Modifier.padding(bottom = 2.dp)
        )

        // Content based on state
        when {
            uiState.showMovePreview && uiState.moveOperations.isNotEmpty() -> {
                MoveOperationsList(
                    operations = uiState.moveOperations,
                    onToggleOperation = viewModel::toggleMoveOperation,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.moveReport != null -> {
                MoveReportDisplay(
                    report = uiState.moveReport!!,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            uiState.categorizationState is CategorizationState.Idle -> {
                if (uiState.scannedFiles.isNotEmpty()) {
                    ScannedFilesList(
                        files = uiState.scannedFiles, modifier = Modifier.fillMaxWidth()
                    )
                } else if (uiState.selectedDirectory != null) {
                    EmptyStateMessage("No files found in '${uiState.selectedDirectory}'. Try selecting a different directory or add some files to scan.")
                } else {
                    EmptyStateMessage("Select a directory and scan for files to get started")
                }
            }

            uiState.categorizationState is CategorizationState.CategorizationComplete -> {
                CategorizationResultsList(
                    results = uiState.categorizationResults,
                    onConfirmResult = viewModel::confirmCategorization,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            else -> {
                // Show progress or other states
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        // Status Display
        StatusDisplay(
            state = uiState.categorizationState,
            scannedFilesCount = uiState.scannedFiles.size,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // Debug info
        Card(modifier = Modifier.padding(bottom = 16.dp)) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Debug Info:", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Selected: ${uiState.selectedDirectory ?: "None"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "State: ${uiState.categorizationState}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Files: ${uiState.scannedFiles.size}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Model Loaded: ${uiState.isModelLoaded}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.isModelLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )

                if (!uiState.isModelLoaded) {
                    Text(
                        "⚠️ Load a model from the side drawer to enable categorization",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (uiState.scannedFiles.isEmpty() && uiState.selectedDirectory != null) {
                    Text(
                        "💡 Press 'Scan Current' to find files in the selected directory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (uiState.scannedFiles.isNotEmpty()) {
                    Text(
                        "✅ Ready to categorize ${uiState.scannedFiles.size} files!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Create Test Files")
                    }

                    Button(
                        onClick = viewModel::refreshModelStatus,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Refresh Model Status")
                    }
                }
            }
        }

        // Error Display
        uiState.error?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        // Debug logging
        LaunchedEffect(uiState) {
            android.util.Log.d(
                "FileCategorizeScreen",
                "UI State updated: selectedDirectory=${uiState.selectedDirectory}, state=${uiState.categorizationState}, scannedFiles=${uiState.scannedFiles.size}, modelLoaded=${uiState.isModelLoaded}"
            )
        }
    }
}

@Composable
private fun DirectorySelectionCard(
    selectedDirectory: String?, onDirectorySelected: (String) -> Unit, modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Selected Directory",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedDirectory != null) {
                Text(
                    text = selectedDirectory,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "No directory selected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    // Try multiple directories to find one with files
                    val directories = listOf(
                        "/storage/emulated/0/Download",
                        "/storage/emulated/0/Documents",
                        "/storage/emulated/0/Pictures",
                        "/storage/emulated/0/DCIM",
                        "/storage/emulated/0/DCIM/Camera",
                        "/storage/emulated/0/Pictures/Screenshots",
                        "/storage/emulated/0"
                    )

                    // Find directory with files, or fallback to first existing directory
                    val dirWithFiles = directories.find { path ->
                        val dir = java.io.File(path)
                        dir.exists() && dir.isDirectory && (dir.listFiles()
                            ?.any { it.isFile } == true)
                    }

                    val selectedDir = dirWithFiles ?: directories.find { java.io.File(it).exists() }
                    ?: directories.first()
                    onDirectorySelected(selectedDir)
                }, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Directory")
            }
        }
    }
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
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // First row - Scanning buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    android.util.Log.d("FileCategorizeScreen", "Scan Current button clicked")
                    onScanDirectory()
                },
                enabled = !uiState.selectedDirectory.isNullOrEmpty(),
                modifier = Modifier.fillMaxWidth(0.48f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan Current")
            }

            Button(
                onClick = onScanAllDirectories,
                enabled = true, // Always enabled for scanning all directories
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Scan All")
            }

            // Second row - Processing buttons
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onCategorizeFiles,
                    enabled = uiState.scannedFiles.isNotEmpty() && uiState.categorizationState == CategorizationState.Idle && uiState.isModelLoaded,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        when {
                            !uiState.isModelLoaded -> "Load Model First"
                            uiState.scannedFiles.isEmpty() -> "Scan Files First"
                            uiState.categorizationState != CategorizationState.Idle -> "Processing..."
                            else -> "Categorize"
                        }
                    )
                }
            }

            // Move operations buttons
            if (uiState.showMovePreview && uiState.moveOperations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSelectAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Select All")
                    }

                    Button(
                        onClick = onDeselectAll,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Deselect All")
                    }

                    Button(
                        onClick = onExecuteMove,
                        enabled = uiState.moveOperations.any { it.isSelected },
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset")
                }
            }
        }


    }
}

@Composable
private fun StatusDisplay(
    state: CategorizationState,
    scannedFilesCount: Int,
    modifier: Modifier = Modifier
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
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (scannedFilesCount > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Found $scannedFilesCount files so far...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is CategorizationState.CategorizingFiles -> {
                    Column {
                        Text("Categorizing files...", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = state.progress, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Processing: ${state.currentFile}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                is CategorizationState.MovingFiles -> {
                    Column {
                        Text("Moving files...", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = state.progress, modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Moving: ${state.currentFile}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                is CategorizationState.CategorizationComplete -> {
                    Text(
                        "Categorization complete! Review and confirm results below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is CategorizationState.FilesMovedSuccessfully -> {
                    Text(
                        "Files moved successfully!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                is CategorizationState.Failed -> {
                    Text(
                        "Error: ${state.error}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(
    message: String, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScannedFilesList(
    files: List<FileItem>, modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Summary header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Scanned Files (${files.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                // File type summary
                val fileTypeCounts = files.groupBy { it.fileType }.mapValues { it.value.size }
                Text(
                    text = fileTypeCounts.entries.take(3)
                        .joinToString(", ") { "${it.value} ${it.key.displayName}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.heightIn(max = 300.dp)) {
                files.take(100).forEach { file -> // Limit display to first 100 files for performance
                    FileItemCard(
                        fileItem = file, modifier = Modifier.padding(vertical = 2.dp)
                    )
                }

                if (files.size > 100) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = "... and ${files.size - 100} more files",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorizationResultsList(
    results: List<CategorizationResult>,
    onConfirmResult: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Categorization Results (${results.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column {
                results.forEachIndexed { index, result ->
                    CategorizationResultCard(
                        result = result,
                        onConfirm = { confirmed -> onConfirmResult(index, confirmed) },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FileItemCard(
    fileItem: FileItem, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = fileItem.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = fileItem.fileType.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = fileItem.sizeFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategorizationResultCard(
    result: CategorizationResult, onConfirm: (Boolean) -> Unit, modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
            containerColor = if (result.isConfirmed) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.fileItem.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "→ ${result.suggestedCategory.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Confidence: ${(result.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (result.reasoning.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = result.reasoning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Checkbox(
                    checked = result.isConfirmed, onCheckedChange = onConfirm
                )
            }
        }
    }
}

private fun createTestFiles(directoryPath: String) {
    try {
        val directory = java.io.File(directoryPath)
        if (!directory.exists()) directory.mkdirs()

        // Create some test files with different types and names
        val testFiles = listOf(
            "receipt_grocery_store.txt" to "Grocery Store Receipt\nDate: 2024-01-15\nTotal: $45.67\nItems: Milk, Bread, Eggs",
            "work_meeting_notes.txt" to "Meeting Notes - Project Planning\nDate: 2024-01-10\nAttendees: John, Sarah, Mike\nAction items: Review budget, Schedule follow-up",
            "passport_copy.txt" to "Passport Information\nDocument Type: Passport\nIssue Date: 2020-05-15\nExpiry Date: 2030-05-15",
            "vacation_photos_list.txt" to "Vacation Photos\nLocation: Hawaii\nDate: Summer 2023\nPhotos: Beach, Sunset, Hiking",
            "software_installer.txt" to "Downloaded Software\nFile: setup.exe\nVersion: 2.1.4\nSize: 150MB",
            "music_playlist.txt" to "My Favorite Songs\nGenre: Rock\nArtist: Various\nTotal: 25 songs",
            "old_temp_file.txt" to "Temporary file\nCreated: 2023-01-01\nStatus: Can be deleted"
        )

        testFiles.forEach { (filename, content) ->
            val file = java.io.File(directory, filename)
            if (!file.exists()) {
                file.writeText(content)
            }
        }

        android.util.Log.d(
            "FileCategorizeScreen",
            "Created ${testFiles.size} test files in $directoryPath"
        )
    } catch (e: Exception) {
        android.util.Log.e("FileCategorizeScreen", "Error creating test files", e)
    }
}

@Composable
private fun MoveOperationsList(
    operations: List<MoveOperation>,
    onToggleOperation: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(true) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with summary and expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Move Operations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    val selectedCount = operations.count { it.isSelected }
                    Text(
                        text = "$selectedCount/${operations.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))

                // Group operations by category
                val groupedOperations = operations.groupBy { it.categoryName }

                Column(
                    modifier = Modifier.heightIn(
                        min = 200.dp,
                        max = 600.dp
                    )
                ) {
                    groupedOperations.forEach { (categoryName, categoryOperations) ->
                        // Category header
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "${categoryOperations.count { it.isSelected }}/${categoryOperations.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        // Operations in this category
                        categoryOperations.forEach { operation ->
                            val globalIndex = operations.indexOf(operation)

                            MoveOperationCard(
                                operation = operation,
                                onToggle = { onToggleOperation(globalIndex) },
                                modifier = Modifier.padding(vertical = 2.dp, horizontal = 8.dp)
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (operation.isSelected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = operation.fileItem.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "→ ${operation.categoryName}/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = operation.fileItem.sizeFormatted,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Checkbox(
                checked = operation.isSelected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun MoveReportDisplay(
    report: MoveReport,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Move Operation Report",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Summary stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ReportStatCard(
                    title = "Total",
                    value = report.totalOperations.toString(),
                    color = MaterialTheme.colorScheme.primary
                )
                ReportStatCard(
                    title = "Success",
                    value = report.successfulMoves.toString(),
                    color = MaterialTheme.colorScheme.tertiary
                )
                ReportStatCard(
                    title = "Failed",
                    value = report.failedMoves.toString(),
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Duration
            Text(
                text = "Duration: ${report.duration}ms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Created directories
            if (report.createdDirectories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Created Directories:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                report.createdDirectories.forEach { dir ->
                    Text(
                        text = "• $dir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp)
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
                    color = MaterialTheme.colorScheme.error
                )
                Column(modifier = Modifier.heightIn(max = 150.dp)) {
                    report.errors.forEach { error ->
                        Text(
                            text = "• $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(6.dp)
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}
