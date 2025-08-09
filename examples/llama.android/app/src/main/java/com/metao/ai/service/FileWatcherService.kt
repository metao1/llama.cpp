package com.metao.ai.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.FileObserver
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.metao.ai.MainActivity
import com.metao.ai.R
import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.model.FileCategory
import com.metao.ai.domain.usecase.ScanDirectoryUseCase
import com.metao.ai.domain.usecase.CategorizeFileUseCase
import com.metao.ai.data.repository.CategorizationStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.inject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class FileProcessingService : Service() {

    companion object {
        private const val TAG = "FileProcessingService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "file_processing_channel"
        private const val DEBOUNCE_DELAY = 2000L // 2 seconds

        // Actions
        const val ACTION_START_MONITORING = "start_monitoring"
        const val ACTION_STOP_MONITORING = "stop_monitoring"
        const val ACTION_SCAN_DIRECTORY = "scan_directory"
        const val ACTION_CATEGORIZE_FILES = "categorize_files"

        // Extras
        const val EXTRA_DIRECTORIES = "directories"
        const val EXTRA_DIRECTORY_PATH = "directory_path"
        const val EXTRA_INCLUDE_SUBDIRECTORIES = "include_subdirectories"
        const val EXTRA_FILE_PATHS = "file_paths"
        const val EXTRA_SESSION_ID = "session_id"

        fun startMonitoring(context: Context, directoryPaths: List<String>) {
            val intent = Intent(context, FileProcessingService::class.java).apply {
                action = ACTION_START_MONITORING
                putStringArrayListExtra(EXTRA_DIRECTORIES, ArrayList(directoryPaths))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopMonitoring(context: Context) {
            val intent = Intent(context, FileProcessingService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }

        fun scanDirectory(context: Context, directoryPath: String, includeSubdirectories: Boolean = false, sessionId: String? = null) {
            val intent = Intent(context, FileProcessingService::class.java).apply {
                action = ACTION_SCAN_DIRECTORY
                putExtra(EXTRA_DIRECTORY_PATH, directoryPath)
                putExtra(EXTRA_INCLUDE_SUBDIRECTORIES, includeSubdirectories)
                sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun categorizeFiles(context: Context, filePaths: List<String>, sessionId: String? = null) {
            val intent = Intent(context, FileProcessingService::class.java).apply {
                action = ACTION_CATEGORIZE_FILES
                putStringArrayListExtra(EXTRA_FILE_PATHS, ArrayList(filePaths))
                sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // Dependencies injected by Koin
    private val scanDirectoryUseCase: ScanDirectoryUseCase by inject()
    private val categorizeFileUseCase: CategorizeFileUseCase by inject()
    private val stateRepository: CategorizationStateRepository by inject()

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var batteryOptimizedWatcher: BatteryOptimizedFileWatcher? = null
    private var currentSessionId: String? = null
    private var isProcessingFiles = false

    // Service binder for communication with UI
    private val binder = FileProcessingBinder()

    inner class FileProcessingBinder : Binder() {
        fun getService(): FileProcessingService = this@FileProcessingService
    }

    // Callback interface for UI updates
    interface ProcessingCallback {
        fun onScanProgress(scannedCount: Int, totalCount: Int)
        fun onScanComplete(files: List<FileItem>)
        fun onCategorizationProgress(processedCount: Int, totalCount: Int)
        fun onCategorizationComplete(results: List<CategorizationResult>)
        fun onError(error: String)
    }

    private var processingCallback: ProcessingCallback? = null

    fun setProcessingCallback(callback: ProcessingCallback?) {
        processingCallback = callback
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "File Processing Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FileProcessingService started with action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_MONITORING -> {
                val directories = intent.getStringArrayListExtra(EXTRA_DIRECTORIES) ?: emptyList()
                startMonitoringInternal(directories)
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoringInternal()
            }
            ACTION_SCAN_DIRECTORY -> {
                val directoryPath = intent.getStringExtra(EXTRA_DIRECTORY_PATH)
                val includeSubdirectories = intent.getBooleanExtra(EXTRA_INCLUDE_SUBDIRECTORIES, false)
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                if (directoryPath != null) {
                    scanDirectoryInternal(directoryPath, includeSubdirectories, sessionId)
                }
            }
            ACTION_CATEGORIZE_FILES -> {
                val filePaths = intent.getStringArrayListExtra(EXTRA_FILE_PATHS) ?: emptyList()
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                categorizeFilesInternal(filePaths, sessionId)
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
            }
        }

        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FileProcessingService destroyed")
        stopMonitoringInternal()
        serviceScope.cancel()
    }

    private fun startMonitoringInternal(directories: List<String>) {
        Log.d(TAG, "Starting file monitoring for ${directories.size} directories")

        startForeground(NOTIFICATION_ID, createNotification("Monitoring file changes..."))

        batteryOptimizedWatcher = BatteryOptimizedFileWatcher(this) { newFiles ->
            Log.d(TAG, "New files detected: ${newFiles.size}")
            showCategorizationNotification(newFiles)
        }
        batteryOptimizedWatcher?.startWatching(directories)
    }

    private fun stopMonitoringInternal() {
        Log.d(TAG, "Stopping file monitoring")
        batteryOptimizedWatcher?.stopWatching()
        batteryOptimizedWatcher = null
        stopForeground(true)
    }

    private fun scanDirectoryInternal(directoryPath: String, includeSubdirectories: Boolean, sessionId: String?) {
        if (isProcessingFiles) {
            Log.w(TAG, "Already processing files, ignoring scan request")
            return
        }

        isProcessingFiles = true
        currentSessionId = sessionId

        serviceScope.launch {
            try {
                startForeground(NOTIFICATION_ID, createNotification("Scanning directory: $directoryPath"))

                Log.d(TAG, "Starting directory scan for: $directoryPath")
                processingCallback?.onScanProgress(0, -1) // Unknown total

                val files = mutableListOf<FileItem>()
                scanDirectoryUseCase(directoryPath, includeSubdirectories).collect { scannedFiles ->
                    files.addAll(scannedFiles)
                    processingCallback?.onScanProgress(files.size, -1)
                    updateNotification("Scanned ${files.size} files...")
                }

                Log.d(TAG, "Directory scan completed: ${files.size} files found")
                processingCallback?.onScanComplete(files)

                // Save to database if session ID provided
                currentSessionId?.let { sessionId ->
                    // Create session if needed and save scan results
                    // This would be handled by the ViewModel typically
                }

                updateNotification("Scan complete: ${files.size} files found")

            } catch (e: Exception) {
                Log.e(TAG, "Error during directory scan", e)
                processingCallback?.onError("Scan failed: ${e.message}")
                updateNotification("Scan failed: ${e.message}")
            } finally {
                isProcessingFiles = false
            }
        }
    }

    private fun categorizeFilesInternal(filePaths: List<String>, sessionId: String?) {
        if (isProcessingFiles) {
            Log.w(TAG, "Already processing files, ignoring categorization request")
            return
        }

        isProcessingFiles = true
        currentSessionId = sessionId

        serviceScope.launch {
            try {
                startForeground(NOTIFICATION_ID, createNotification("Categorizing ${filePaths.size} files..."))

                Log.d(TAG, "Starting file categorization for ${filePaths.size} files")
                processingCallback?.onCategorizationProgress(0, filePaths.size)

                val results = mutableListOf<CategorizationResult>()
                val categories = FileCategory.getDefaultCategories()

                filePaths.forEachIndexed { index, filePath ->
                    try {
                        val file = File(filePath)
                        if (file.exists() && file.isFile) {
                            val fileItem = createFileItem(file)
                            val result = categorizeFileUseCase(fileItem, categories).first()
                            results.add(result)

                            processingCallback?.onCategorizationProgress(index + 1, filePaths.size)
                            updateNotification("Categorized ${index + 1}/${filePaths.size} files...")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error categorizing file: $filePath", e)
                    }
                }

                Log.d(TAG, "File categorization completed: ${results.size} results")
                processingCallback?.onCategorizationComplete(results)

                // Save to database if session ID provided
                currentSessionId?.let { sessionId ->
                    stateRepository.saveCategorizationResults(sessionId, results)
                }

                updateNotification("Categorization complete: ${results.size} files processed")

            } catch (e: Exception) {
                Log.e(TAG, "Error during file categorization", e)
                processingCallback?.onError("Categorization failed: ${e.message}")
                updateNotification("Categorization failed: ${e.message}")
            } finally {
                isProcessingFiles = false
            }
        }
    }

    private fun createFileItem(file: File): FileItem {
        return FileItem(
            file = file,
            name = file.name,
            path = file.absolutePath,
            sizeBytes = file.length(),
            extension = file.extension,
            mimeType = null,
            lastModified = java.util.Date(file.lastModified()),
            isDirectory = false,
            contentPreview = null
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "File Processing",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "File scanning and categorization operations"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Processing")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showCategorizationNotification(files: List<FileItem>) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("auto_categorize", true)
            putExtra("new_files_count", files.size)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("New Files Detected!")
            .setContentText("${files.size} new files ready for categorization")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                R.drawable.ic_launcher_foreground,
                "Categorize Now",
                pendingIntent
            )
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 1, notification)

        Log.d(TAG, "Showed categorization notification for ${files.size} files")
    }

}
