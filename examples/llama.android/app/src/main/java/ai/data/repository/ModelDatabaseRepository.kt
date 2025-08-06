package com.metao.ai.data.repository

import com.metao.ai.data.database.ModelDao
import com.metao.ai.data.database.ModelEntity
import com.metao.ai.data.database.toDomainModel
import com.metao.ai.data.database.toEntity
import com.metao.ai.domain.model.ModelInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelDatabaseRepository(
    private val modelDao: ModelDao
) {
    
    fun getAllModels(): Flow<List<ModelInfo>> {
        return modelDao.getAllModels().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    suspend fun getModelById(modelId: String): ModelInfo? {
        return modelDao.getModelById(modelId)?.toDomainModel()
    }
    
    fun getCustomModels(): Flow<List<ModelInfo>> {
        return modelDao.getCustomModels().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    fun getBuiltInModels(): Flow<List<ModelInfo>> {
        return modelDao.getBuiltInModels().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    suspend fun insertModel(model: ModelInfo, isCustom: Boolean = false) {
        modelDao.insertModel(model.toEntity(isCustom))
    }
    
    suspend fun insertModels(models: List<ModelInfo>, isCustom: Boolean = false) {
        val entities = models.map { it.toEntity(isCustom) }
        modelDao.insertModels(entities)
    }
    
    suspend fun updateModel(model: ModelInfo, isCustom: Boolean = false) {
        modelDao.updateModel(model.toEntity(isCustom))
    }
    
    suspend fun updateDownloadStatus(modelId: String, isDownloaded: Boolean) {
        modelDao.updateDownloadStatus(modelId, isDownloaded)
    }
    
    suspend fun deleteModel(model: ModelInfo) {
        modelDao.deleteModel(model.toEntity())
    }
    
    suspend fun deleteModelById(modelId: String) {
        modelDao.deleteModelById(modelId)
    }
    
    suspend fun deleteAllCustomModels() {
        modelDao.deleteAllCustomModels()
    }
    
    suspend fun getModelCount(): Int {
        return modelDao.getModelCount()
    }
    
    suspend fun getDownloadedModelCount(): Int {
        return modelDao.getDownloadedModelCount()
    }
    
    suspend fun initializeDefaultModels(defaultModels: List<ModelInfo>) {
        val count = getModelCount()
        if (count == 0) {
            // Only insert default models if database is empty
            insertModels(defaultModels, isCustom = false)
        }
    }
}
