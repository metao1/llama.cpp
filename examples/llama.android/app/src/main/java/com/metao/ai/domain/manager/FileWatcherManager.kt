package com.metao.ai.domain.manager

import android.content.Context
import android.util.Log
import com.metao.ai.data.repository.SettingsRepository
import com.metao.ai.service.FileProcessingService
import com.metao.ai.service.BatteryOptimizedFileWatcher
import com.metao.ai.domain.model.FileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class FileWatcherManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val TAG = "FileWatcherManager"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isServiceRunning = false
    private var batteryOptimizedWatcher: BatteryOptimizedFileWatcher? = null

    // Callback for when new files are detected
    var onNewFilesDetected: ((List<FileItem>) -> Unit)? = null

    init {
        // Monitor settings changes and update service accordingly
        scope.launch {
            combine(
                settingsRepository.getFileWatchingEnabledFlow(),
                settingsRepository.getWatchedDirectoriesFlow()
            ) { enabled, directories ->
                Pair(enabled, directories)
            }.collect { (enabled, directories) ->
                updateFileWatchingService(enabled, directories)
            }
        }
    }

    suspend fun startFileWatching() {
        Log.d(TAG, "Starting file watching")
        settingsRepository.setFileWatchingEnabled(true)
    }

    suspend fun stopFileWatching() {
        Log.d(TAG, "Stopping file watching")
        settingsRepository.setFileWatchingEnabled(false)
    }

    suspend fun addWatchedDirectory(path: String) {
        Log.d(TAG, "Adding watched directory: $path")
        settingsRepository.addWatchedDirectory(path)
    }

    suspend fun removeWatchedDirectory(path: String) {
        Log.d(TAG, "Removing watched directory: $path")
        settingsRepository.removeWatchedDirectory(path)
    }

    suspend fun isFileWatchingEnabled(): Boolean {
        return settingsRepository.isFileWatchingEnabled()
    }

    suspend fun getWatchedDirectories(): List<String> {
        return settingsRepository.getWatchedDirectories()
    }

    private fun updateFileWatchingService(enabled: Boolean, directories: List<String>) {
        Log.d(TAG, "Updating file watching service: enabled=$enabled, directories=${directories.size}")

        if (enabled && directories.isNotEmpty()) {
            if (!isServiceRunning) {
                // Use battery-optimized watcher instead of continuous service
                FileProcessingService.startMonitoring(context, directories)
                isServiceRunning = true
                Log.d(TAG, "Battery-optimized file watcher started")
            }
        } else {
            if (isServiceRunning) {
                FileProcessingService.stopMonitoring(context)
                isServiceRunning = false
                Log.d(TAG, "Battery-optimized file watcher stopped")
            }
        }
    }



    fun onDestroy() {
        if (isServiceRunning) {
            FileProcessingService.stopMonitoring(context)
            isServiceRunning = false
        }
    }
}
