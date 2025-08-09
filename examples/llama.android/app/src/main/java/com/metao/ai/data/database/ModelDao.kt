package com.metao.ai.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelDao {
    
    @Query("SELECT * FROM models ORDER BY dateAdded DESC")
    fun getAllModels(): Flow<List<ModelEntity>>
    
    @Query("SELECT * FROM models WHERE id = :modelId")
    suspend fun getModelById(modelId: String): ModelEntity?
    
    @Query("SELECT * FROM models WHERE isCustom = 1 ORDER BY dateAdded DESC")
    fun getCustomModels(): Flow<List<ModelEntity>>
    
    @Query("SELECT * FROM models WHERE isCustom = 0 ORDER BY dateAdded DESC")
    fun getBuiltInModels(): Flow<List<ModelEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: ModelEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModels(models: List<ModelEntity>)
    
    @Update
    suspend fun updateModel(model: ModelEntity)
    
    @Query("UPDATE models SET isDownloaded = :isDownloaded WHERE id = :modelId")
    suspend fun updateDownloadStatus(modelId: String, isDownloaded: Boolean)
    
    @Delete
    suspend fun deleteModel(model: ModelEntity)
    
    @Query("DELETE FROM models WHERE id = :modelId")
    suspend fun deleteModelById(modelId: String)
    
    @Query("DELETE FROM models WHERE isCustom = 1")
    suspend fun deleteAllCustomModels()
    
    @Query("SELECT COUNT(*) FROM models")
    suspend fun getModelCount(): Int
    
    @Query("SELECT COUNT(*) FROM models WHERE isDownloaded = 1")
    suspend fun getDownloadedModelCount(): Int
}
