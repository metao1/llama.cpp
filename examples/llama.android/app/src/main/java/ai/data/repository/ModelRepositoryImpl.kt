package com.metao.ai.data.repository

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.llama.cpp.LLamaAndroid
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.database.getLongOrNull
import androidx.core.net.toUri
import com.metao.ai.domain.model.DownloadState
import com.metao.ai.domain.model.ModelInfo
import com.metao.ai.domain.model.ModelLoadState
import com.metao.ai.domain.model.TextGenerationState
import com.metao.ai.domain.repository.ModelRepository
import com.metao.ai.domain.util.MessageFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class ModelRepositoryImpl(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val llamaAndroid: LLamaAndroid,
    private val databaseRepository: ModelDatabaseRepository
) : ModelRepository {

    companion object {
        private const val TAG = "ModelRepository"
        private const val NOTIFICATION_CHANNEL_ID = "model_downloads"
        private const val NOTIFICATION_CHANNEL_NAME = "Model Downloads"
        private const val DOWNLOAD_NOTIFICATION_ID = 1001
        private const val ERROR_NOTIFICATION_ID = 1002
    }

    private var isModelLoaded = false
    private var isTestModel = false

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    override suspend fun getAvailableModels(): List<ModelInfo> {
        return try {
            Log.d(TAG, "Getting available models from database...")

            // Initialize default models if database is empty
            initializeDefaultModelsIfNeeded()

            // Get all models from database using first() to get single emission
            val models = databaseRepository.getAllModels().first()
            Log.d(TAG, "Retrieved ${models.size} models from database")

            // Update download status based on actual file existence
            val updatedModels = models.map { model ->
                val actuallyDownloaded = model.destinationFile.exists()
                if (model.isDownloaded != actuallyDownloaded) {
                    Log.d(TAG, "Updating download status for ${model.name}: $actuallyDownloaded")
                    databaseRepository.updateDownloadStatus(model.id, actuallyDownloaded)
                    model.copy(isDownloaded = actuallyDownloaded)
                } else {
                    model
                }
            }

            Log.d(TAG, "Returning ${updatedModels.size} models")
            updatedModels
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get models from database", e)
            Log.e(TAG, "Exception details: ${e.message}")
            emptyList()
        }
    }

    private suspend fun initializeDefaultModelsIfNeeded() {
        try {
            Log.d(TAG, "Checking if default models need to be initialized...")

            val modelsDir = File(context.getExternalFilesDir(null), "models")
            modelsDir.mkdirs()
            Log.d(TAG, "Models directory: ${modelsDir.absolutePath}")

            val defaultModels = listOf(
                ModelInfo(
                    id = "test-model",
                    name = "Test Model",
                    description = "Small test file for testing",
                    sourceUrl = "https://huggingface.co/microsoft/DialoGPT-medium/resolve/main/README.md".toUri(),
                    destinationFile = File(modelsDir, "test-model.txt"),
                    sizeBytes = 1L * 1024 * 1024,
                    isDownloaded = File(modelsDir, "test-model.txt").exists()
                ),
                ModelInfo(
                    id = "tinyllama",
                    name = "TinyLlama 1.1B",
                    description = "Small chat model (1.1B parameters)",
                    sourceUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf".toUri(),
                    destinationFile = File(modelsDir, "tinyllama.gguf"),
                    sizeBytes = 669L * 1024 * 1024,
                    isDownloaded = File(modelsDir, "tinyllama.gguf").exists()
                )
            )

            Log.d(TAG, "Initializing ${defaultModels.size} default models...")
            databaseRepository.initializeDefaultModels(defaultModels)
            Log.d(TAG, "Default models initialization completed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize default models", e)
            Log.e(TAG, "Exception details: ${e.message}")
        }
    }

    override suspend fun addCustomModel(modelInfo: ModelInfo) {
        try {
            databaseRepository.insertModel(modelInfo, isCustom = true)
            Log.d(TAG, "Custom model added to database: ${modelInfo.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add custom model to database", e)
            throw e
        }
    }

    override suspend fun downloadModel(modelInfo: ModelInfo): Flow<DownloadState> = flow {
        emit(DownloadState.Preparing)

        if (modelInfo.destinationFile.exists()) {
            modelInfo.destinationFile.delete()
        }
        modelInfo.destinationFile.parentFile?.mkdirs()

        val request = DownloadManager.Request(modelInfo.sourceUrl).apply {
            setTitle("Downloading ${modelInfo.name}")
            setDescription("Downloading model: ${modelInfo.name}")
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setDestinationInExternalFilesDir(context, "models", modelInfo.destinationFile.name)
            addRequestHeader("User-Agent", "Mozilla/5.0 (Android)")
        }

        val downloadId = downloadManager.enqueue(request)
        emit(DownloadState.Downloading(0f))

        var attempts = 0
        while (attempts < 1800) { // 30 minutes max
            attempts++

            val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            if (cursor == null || !cursor.moveToFirst()) {
                cursor?.close()
                val errorMsg = "Download query failed"
                showDownloadErrorNotification(modelInfo.name, errorMsg)
                emit(DownloadState.Failed(errorMsg))
                return@flow
            }

            val columnStatus =  cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val columnReason = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
            val status = cursor.getInt(columnStatus)
            val reason = cursor.getInt(columnReason)
            val bytesDownloaded = cursor.getLongOrNull(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)) ?: 0
            val bytesTotal = cursor.getLongOrNull(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)) ?: 1

            cursor.close()

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    dismissDownloadNotification()
                    showDownloadCompleteNotification(modelInfo.name)

                    // Update database to mark as downloaded
                    try {
                        databaseRepository.updateDownloadStatus(modelInfo.id, true)
                        Log.d(TAG, "Updated download status in database for ${modelInfo.name}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update download status in database", e)
                    }

                    emit(DownloadState.Completed)
                    return@flow
                }

                DownloadManager.STATUS_FAILED -> {
                    val errorMessage = getDownloadErrorMessage(reason)
                    Log.e(
                        TAG,
                        "Download failed for ${modelInfo.name}: $errorMessage (reason: $reason)"
                    )

                    // Show error notification FIRST, then dismiss progress
                    showDownloadErrorNotification(modelInfo.name, errorMessage)

                    // Wait a moment then dismiss progress notification
                    kotlinx.coroutines.delay(500)
                    dismissDownloadNotification()

                    emit(DownloadState.Failed("Download failed: $errorMessage"))
                    return@flow
                }

                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                    val progress =
                        if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal else 0f
                    showDownloadProgressNotification(modelInfo.name, progress)
                    emit(DownloadState.Downloading(progress))
                }
            }

            delay(1000)
        }

        dismissDownloadNotification()
        showDownloadErrorNotification(modelInfo.name, "Download timeout after 30 minutes")
        emit(DownloadState.Failed("Download timeout"))
    }.flowOn(Dispatchers.IO)

    override suspend fun loadModel(modelPath: String): Flow<ModelLoadState> = flow {
        emit(ModelLoadState.Loading)

        try {
            if (modelPath.endsWith(".txt")) {
                // Test model
                isTestModel = true
                isModelLoaded = true
                emit(ModelLoadState.Loaded)
            } else {
                // Real model
                llamaAndroid.load(modelPath)
                isTestModel = false
                isModelLoaded = true
                emit(ModelLoadState.Loaded)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            isModelLoaded = false
            emit(ModelLoadState.Failed(e.message ?: "Failed to load model"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun generateText(prompt: String, useChat: Boolean): Flow<TextGenerationState> = flow {
        emit(TextGenerationState.Generating)
        if (!isModelLoaded) {
            emit(TextGenerationState.Failed("No model loaded"))
            return@flow
        }
        try {
            var accumulatedResponse = ""
            var lastCleanLength = 0

            llamaAndroid.send(prompt, false).collect { token ->
                accumulatedResponse += token
                Log.d(
                    TAG,
                    "Token: '$token', Accumulated: '${accumulatedResponse.take(100)}...'"
                )

                // Extract clean content from accumulated response
                val cleanResponse = MessageFormatter.extractResponse(accumulatedResponse)
                Log.d(TAG, "Clean response: '${cleanResponse.take(50)}...'")

                // Only emit new content that wasn't emitted before
                if (cleanResponse.length > lastCleanLength) {
                    val newContent = cleanResponse.substring(lastCleanLength)
                    if (newContent.isNotEmpty()) {
                        Log.d(TAG, "Emitting new content: '$newContent'")
                        emit(TextGenerationState.TokenGenerated(newContent))
                        lastCleanLength = cleanResponse.length
                    }
                }
            }
            emit(TextGenerationState.Completed)
        } catch (e: Exception) {
            Log.e(TAG, "Text generation failed", e)
            emit(TextGenerationState.Failed(e.message ?: "Generation failed"))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun clearMessages() {
        // Clear conversation history if needed
        llamaAndroid.unload()
    }

    override suspend fun benchmark(nThreads: Int, nLayers: Int, nRepeat: Int): String {
        return try {
            llamaAndroid.bench(nThreads, nLayers, nRepeat)
        } catch (e: Exception) {
            "Benchmark failed: ${e.message}"
        }
    }

    override suspend fun deleteModel(modelInfo: ModelInfo): Result<Unit> {
        return try {
            if (modelInfo.destinationFile.exists()) {
                modelInfo.destinationFile.delete()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun isModelLoaded(): Boolean = isModelLoaded

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel for Android O+")

            // Delete existing channel first (in case of issues)
            try {
                notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID)
                Log.d(TAG, "Deleted existing notification channel")
            } catch (e: Exception) {
                Log.d(TAG, "No existing channel to delete")
            }

            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Model download notifications"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created successfully: $NOTIFICATION_CHANNEL_ID")

            // Verify channel was created
            val createdChannel = notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID)
            Log.d(
                TAG,
                "Channel verification - exists: ${createdChannel != null}, importance: ${createdChannel?.importance}"
            )

        } else {
            Log.d(TAG, "Pre-Android O, no notification channel needed")
        }
    }

    private fun showDownloadProgressNotification(modelName: String, progress: Float) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission, cannot show progress notification")
            return
        }

        try {
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Downloading $modelName")
                .setContentText("${(progress * 100).toInt()}% complete")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, (progress * 100).toInt(), false)
                .setOngoing(true)
                .setAutoCancel(false)
                .build()

            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
            Log.d(TAG, "Showing progress notification for $modelName: ${(progress * 100).toInt()}%")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show progress notification", e)
        }
    }

    private fun showDownloadCompleteNotification(modelName: String) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission, cannot show complete notification")
            return
        }

        try {
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Download Complete")
                .setContentText("$modelName has been downloaded successfully")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(DOWNLOAD_NOTIFICATION_ID, notification)
            Log.d(TAG, "Showing complete notification for $modelName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show complete notification", e)
        }
    }

    private fun showDownloadErrorNotification(modelName: String, error: String) {
        Log.d(TAG, "=== showDownloadErrorNotification START ===")
        Log.d(TAG, "Model: $modelName, Error: $error")

        // Always show toast for immediate feedback
        Toast.makeText(context, "Download Failed: $modelName - $error", Toast.LENGTH_LONG).show()
        Log.d(TAG, "Toast shown for error")

        // Check permission and show notification
        val hasPermission = hasNotificationPermission()
        Log.d(TAG, "Notification permission status: $hasPermission")

        if (!hasPermission) {
            Log.w(TAG, "No notification permission, only showing toast")
            return
        }

        try {
            Log.d(TAG, "Creating error notification...")
            Log.d(TAG, "Using channel: $NOTIFICATION_CHANNEL_ID")
            Log.d(TAG, "Using notification ID: $ERROR_NOTIFICATION_ID")

            // Create simple, direct notification with different ID than progress
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Download Failed")
                .setContentText("$modelName: $error")
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setOnlyAlertOnce(false)  // Always alert for errors
                .build()

            Log.d(TAG, "Notification object created successfully")
            Log.d(
                TAG,
                "About to call notificationManager.notify($ERROR_NOTIFICATION_ID, notification)"
            )

            notificationManager.notify(ERROR_NOTIFICATION_ID, notification)

            Log.d(TAG, "notificationManager.notify() call completed")
            Log.d(TAG, "=== showDownloadErrorNotification END ===")

        } catch (e: Exception) {
            Log.e(TAG, "Exception in showDownloadErrorNotification", e)
            Log.e(TAG, "Exception details: ${e.message}")
            Log.e(TAG, "Exception stack trace: ${e.stackTrace.joinToString("\n")}")
        }
    }

    private fun dismissDownloadNotification() {
        Log.d(TAG, "Dismissing download notification ID: $DOWNLOAD_NOTIFICATION_ID")
        notificationManager.cancel(DOWNLOAD_NOTIFICATION_ID)
        Log.d(TAG, "Download notification dismissed")
    }

    private fun hasNotificationPermission(): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Android 13+ notification permission: $permission")
            permission
        } else {
            val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            Log.d(TAG, "Notifications enabled (pre-Android 13): $enabled")
            enabled
        }

        Log.d(TAG, "Overall notification permission: $hasPermission")
        return hasPermission
    }


    private fun getDownloadErrorMessage(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "No external storage device found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "Storage issue"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server error"
            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
            else -> "Error code: $reason"
        }
    }
}
