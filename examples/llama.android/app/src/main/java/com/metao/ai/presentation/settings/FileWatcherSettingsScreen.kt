package com.metao.ai.presentation.settings

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.metao.ai.data.repository.SettingsRepository
import com.metao.ai.domain.manager.FileWatcherManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class FileWatcherSettingsUiState(
    val isFileWatchingEnabled: Boolean = false,
    val isAutoCategorizationEnabled: Boolean = true,
    val watchedDirectories: List<String> = emptyList(),
    val showAddDirectoryDialog: Boolean = false
)

class FileWatcherSettingsViewModel(
    private val fileWatcherManager: FileWatcherManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    companion object {
        private const val TAG = "FileWatcherSettingsVM"
    }

    private val _uiState = MutableStateFlow(FileWatcherSettingsUiState())
    val uiState: StateFlow<FileWatcherSettingsUiState> = _uiState.asStateFlow()

    init {
        // Observe settings changes
        viewModelScope.launch {
            combine(
                settingsRepository.getFileWatchingEnabledFlow(),
                settingsRepository.getWatchedDirectoriesFlow(),
                flow { emit(settingsRepository.isAutoCategorizationEnabled()) }
            ) { enabled, directories, autoEnabled ->
                Triple(enabled, directories, autoEnabled)
            }.collect { (enabled, directories, autoEnabled) ->
                _uiState.update {
                    it.copy(
                        isFileWatchingEnabled = enabled,
                        watchedDirectories = directories,
                        isAutoCategorizationEnabled = autoEnabled
                    )
                }
            }
        }
    }

    fun toggleFileWatching() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value.isFileWatchingEnabled
                if (currentState) {
                    fileWatcherManager.stopFileWatching()
                } else {
                    fileWatcherManager.startFileWatching()
                }
                Log.d(TAG, "File watching toggled to: ${!currentState}")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling file watching", e)
            }
        }
    }

    fun toggleAutoCategorizationEnabled() {
        viewModelScope.launch {
            try {
                val currentState = _uiState.value.isAutoCategorizationEnabled
                settingsRepository.setAutoCategorizationEnabled(!currentState)
                Log.d(TAG, "Auto categorization toggled to: ${!currentState}")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling auto categorization", e)
            }
        }
    }

    fun addWatchedDirectory(path: String) {
        viewModelScope.launch {
            try {
                fileWatcherManager.addWatchedDirectory(path)
                Log.d(TAG, "Added watched directory: $path")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding watched directory", e)
            }
        }
    }

    fun removeWatchedDirectory(path: String) {
        viewModelScope.launch {
            try {
                fileWatcherManager.removeWatchedDirectory(path)
                Log.d(TAG, "Removed watched directory: $path")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing watched directory", e)
            }
        }
    }

    fun showAddDirectoryDialog() {
        _uiState.update { it.copy(showAddDirectoryDialog = true) }
    }

    fun hideAddDirectoryDialog() {
        _uiState.update { it.copy(showAddDirectoryDialog = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileWatcherSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: FileWatcherSettingsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "File Monitoring Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Enable/Disable File Watching
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto File Monitoring",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Automatically detect new files and suggest categorization",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = uiState.isFileWatchingEnabled,
                        onCheckedChange = { viewModel.toggleFileWatching() }
                    )
                }

                if (uiState.isFileWatchingEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Auto Categorization",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = uiState.isAutoCategorizationEnabled,
                            onCheckedChange = { viewModel.toggleAutoCategorizationEnabled() }
                        )
                    }
                }
            }
        }

        // Watched Directories
        if (uiState.isFileWatchingEnabled) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Watched Directories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        IconButton(onClick = viewModel::showAddDirectoryDialog) {
                            Icon(Icons.Default.Add, contentDescription = "Add Directory")
                        }
                    }

                    Text(
                        text = "Directories being monitored for new files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (uiState.watchedDirectories.isEmpty()) {
                        Text(
                            text = "No directories being watched",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.heightIn(max = 300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.watchedDirectories.forEach { directory ->
                                WatchedDirectoryItem(
                                    directory = directory,
                                    onRemove = { viewModel.removeWatchedDirectory(directory) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Status Information
        if (uiState.isFileWatchingEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📱 Service Status",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "File monitoring is active. You'll receive notifications when new files are detected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }

    // Add Directory Dialog
    if (uiState.showAddDirectoryDialog) {
        AddDirectoryDialog(
            onDismiss = viewModel::hideAddDirectoryDialog,
            onAddDirectory = viewModel::addWatchedDirectory
        )
    }
}

@Composable
private fun WatchedDirectoryItem(
    directory: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = directory,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddDirectoryDialog(
    onDismiss: () -> Unit,
    onAddDirectory: (String) -> Unit
) {
    var directoryPath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Directory to Watch") },
        text = {
            Column {
                Text(
                    text = "Enter the full path of the directory to monitor:",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = directoryPath,
                    onValueChange = { directoryPath = it },
                    label = { Text("Directory Path") },
                    placeholder = { Text("/storage/emulated/0/Download") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (directoryPath.isNotBlank()) {
                        onAddDirectory(directoryPath.trim())
                        onDismiss()
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
