package com.metao.ai.data.repository

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.llama.cpp.LLamaAndroid
import android.util.Log
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class ModelRepositoryImpl(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val llamaAndroid: LLamaAndroid
) : ModelRepository {

    companion object {
        private const val TAG = "ModelRepository"
    }

    private var isModelLoaded = false
    private var isTestModel = false

    override suspend fun getAvailableModels(): List<ModelInfo> {
        return try {
            val modelsDir = File(context.getExternalFilesDir(null), "models")
            modelsDir.mkdirs()

            listOf(
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get models", e)
            emptyList()
        }
    }

    @SuppressLint("Range")
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
                emit(DownloadState.Failed("Download query failed"))
                return@flow
            }

            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            val reason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON))
            val bytesDownloaded =
                cursor.getLongOrNull(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    ?: 0
            val bytesTotal =
                cursor.getLongOrNull(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    ?: 1

            cursor.close()

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    emit(DownloadState.Completed)
                    return@flow
                }

                DownloadManager.STATUS_FAILED -> {
                    emit(DownloadState.Failed("Download failed: $reason"))
                    return@flow
                }

                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PENDING -> {
                    val progress =
                        if (bytesTotal > 0) bytesDownloaded.toFloat() / bytesTotal else 0f
                    emit(DownloadState.Downloading(progress))
                }
            }

            delay(1000)
        }

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

    override suspend fun generateText(prompt: String, useChat: Boolean): Flow<TextGenerationState> =
        flow {
            emit(TextGenerationState.Generating)

            if (!isModelLoaded) {
                emit(TextGenerationState.Failed("No model loaded"))
                return@flow
            }

            try {
                if (isTestModel) {
                    // Test model response
                    val response = "Hello! This is a test response to: $prompt"
                    response.forEach { char ->
                        emit(TextGenerationState.TokenGenerated(char.toString()))
                        delay(50)
                    }
                } else {
                    // Real model response with proper format handling
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
                }
                emit(TextGenerationState.Completed)
            } catch (e: Exception) {
                Log.e(TAG, "Text generation failed", e)
                emit(TextGenerationState.Failed(e.message ?: "Generation failed"))
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun clearMessages() {
        // Clear conversation history if needed
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
}
