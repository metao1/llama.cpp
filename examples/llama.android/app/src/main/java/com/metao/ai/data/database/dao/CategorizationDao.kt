package com.metao.ai.data.database.dao

import androidx.room.*
import com.metao.ai.data.database.entities.CategorizationResultEntity
import com.metao.ai.data.database.entities.CategorizationSessionEntity
import com.metao.ai.data.database.entities.MoveOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategorizationDao {
    
    // Session operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: CategorizationSessionEntity)
    
    @Update
    suspend fun updateSession(session: CategorizationSessionEntity)
    
    @Query("SELECT * FROM categorization_sessions ORDER BY lastAccessedAt DESC")
    fun getAllSessions(): Flow<List<CategorizationSessionEntity>>
    
    @Query("SELECT * FROM categorization_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): CategorizationSessionEntity?
    
    @Query("SELECT * FROM categorization_sessions ORDER BY lastAccessedAt DESC LIMIT 1")
    suspend fun getLastSession(): CategorizationSessionEntity?
    
    @Query("UPDATE categorization_sessions SET lastAccessedAt = :timestamp WHERE sessionId = :sessionId")
    suspend fun updateLastAccessed(sessionId: String, timestamp: Long = System.currentTimeMillis())
    
    // Categorization results operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategorizationResult(result: CategorizationResultEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategorizationResults(results: List<CategorizationResultEntity>)
    
    @Update
    suspend fun updateCategorizationResult(result: CategorizationResultEntity)
    
    @Query("SELECT * FROM categorization_results WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getCategorizationResultsForSession(sessionId: String): Flow<List<CategorizationResultEntity>>
    
    @Query("SELECT * FROM categorization_results WHERE filePath = :filePath")
    suspend fun getCategorizationResultByPath(filePath: String): CategorizationResultEntity?
    
    @Query("DELETE FROM categorization_results WHERE sessionId = :sessionId")
    suspend fun deleteCategorizationResultsForSession(sessionId: String)
    
    // Move operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoveOperation(operation: MoveOperationEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMoveOperations(operations: List<MoveOperationEntity>)
    
    @Update
    suspend fun updateMoveOperation(operation: MoveOperationEntity)
    
    @Query("SELECT * FROM move_operations WHERE sessionId = :sessionId ORDER BY createdAt DESC")
    fun getMoveOperationsForSession(sessionId: String): Flow<List<MoveOperationEntity>>
    
    @Query("UPDATE move_operations SET isSelected = :isSelected WHERE sessionId = :sessionId")
    suspend fun updateAllMoveOperationsSelection(sessionId: String, isSelected: Boolean)
    
    @Query("UPDATE move_operations SET isSelected = :isSelected WHERE id = :operationId")
    suspend fun updateMoveOperationSelection(operationId: String, isSelected: Boolean)
    
    @Query("UPDATE move_operations SET isExecuted = :isExecuted, executedAt = :executedAt, executionResult = :result WHERE id = :operationId")
    suspend fun updateMoveOperationExecution(operationId: String, isExecuted: Boolean, executedAt: Long, result: String)
    
    @Query("DELETE FROM move_operations WHERE sessionId = :sessionId")
    suspend fun deleteMoveOperationsForSession(sessionId: String)
    
    // Cleanup operations
    @Query("DELETE FROM categorization_sessions WHERE createdAt < :timestamp")
    suspend fun deleteOldSessions(timestamp: Long)
    
    @Query("DELETE FROM categorization_results WHERE createdAt < :timestamp")
    suspend fun deleteOldCategorizationResults(timestamp: Long)
    
    @Query("DELETE FROM move_operations WHERE createdAt < :timestamp")
    suspend fun deleteOldMoveOperations(timestamp: Long)
    
    // Statistics
    @Query("SELECT COUNT(*) FROM categorization_results WHERE sessionId = :sessionId")
    suspend fun getCategorizationResultsCount(sessionId: String): Int
    
    @Query("SELECT COUNT(*) FROM move_operations WHERE sessionId = :sessionId AND isSelected = 1")
    suspend fun getSelectedMoveOperationsCount(sessionId: String): Int
    
    @Query("SELECT COUNT(*) FROM move_operations WHERE sessionId = :sessionId AND isExecuted = 1 AND executionResult = 'success'")
    suspend fun getSuccessfulMoveOperationsCount(sessionId: String): Int
}
