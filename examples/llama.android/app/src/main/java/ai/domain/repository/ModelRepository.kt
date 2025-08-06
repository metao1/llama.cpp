package com.metao.ai.domain.repository

import com.metao.ai.domain.model.DownloadState
import com.metao.ai.domain.model.ModelInfo
import com.metao.ai.domain.model.ModelLoadState
import com.metao.ai.domain.model.TextGenerationState
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    suspend fun getAvailableModels(): List<ModelInfo>
    suspend fun addCustomModel(modelInfo: ModelInfo)
    suspend fun downloadModel(modelInfo: ModelInfo): Flow<DownloadState>
    suspend fun loadModel(modelPath: String): Flow<ModelLoadState>
    suspend fun generateText(prompt: String, useChat: Boolean = true): Flow<TextGenerationState>
    suspend fun clearMessages()
    suspend fun benchmark(nThreads: Int, nLayers: Int, nRepeat: Int): String
    suspend fun deleteModel(modelInfo: ModelInfo): Result<Unit>
    suspend fun isModelLoaded(): Boolean
}
