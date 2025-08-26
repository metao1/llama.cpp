package com.metao.ai.data.repository

import com.metao.ai.data.database.ModelDao
import com.metao.ai.data.database.toDomainModel
import com.metao.ai.data.database.toEntity
import com.metao.ai.domain.model.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelDatabaseRepository(
    private val modelDao: ModelDao,
) {
    fun getAllModels(): Flow<List<ModelInfo>> =
        modelDao.getAllModels().map { entities ->
            entities.map { it.toDomainModel() }
        }

    suspend fun insertModel(
        model: ModelInfo,
        isCustom: Boolean = false,
    ) {
        modelDao.insertModel(model.toEntity(isCustom))
    }

    suspend fun insertModels(
        models: List<ModelInfo>,
        isCustom: Boolean = false,
    ) {
        val entities = models.map { it.toEntity(isCustom) }
        modelDao.insertModels(entities)
    }

    suspend fun updateDownloadStatus(
        modelId: String,
        isDownloaded: Boolean,
    ) {
        modelDao.updateDownloadStatus(modelId, isDownloaded)
    }

    suspend fun getModelCount(): Int = modelDao.getModelCount()

    suspend fun initializeDefaultModels(defaultModels: List<ModelInfo>) {
        val count = getModelCount()
        if (count == 0) {
            // Only insert default models if database is empty
            insertModels(defaultModels, isCustom = false)
        }
    }
}
