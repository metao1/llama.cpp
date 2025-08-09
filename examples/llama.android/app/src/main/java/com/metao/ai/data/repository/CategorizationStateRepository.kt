package com.metao.ai.data.repository

import com.metao.ai.data.database.dao.CategorizationDao
import com.metao.ai.data.database.entities.CategorizationResultEntity
import com.metao.ai.data.database.entities.CategorizationSessionEntity
import com.metao.ai.data.database.entities.MoveOperationEntity
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.model.MoveOperation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface CategorizationStateRepository {
    suspend fun createSession(directoryPath: String): String
    suspend fun getLastSession(): CategorizationSessionEntity?
    suspend fun updateSessionStats(sessionId: String, scanned: Int, categorized: Int, moved: Int, failed: Int)
    suspend fun saveCategorizationResults(sessionId: String, results: List<CategorizationResult>)
    suspend fun saveMoveOperations(sessionId: String, operations: List<MoveOperation>)
    suspend fun getCategorizationResults(sessionId: String): Flow<List<CategorizationResult>>
    suspend fun getMoveOperations(sessionId: String): Flow<List<MoveOperation>>
    suspend fun updateMoveOperationSelection(sessionId: String, operationIndex: Int, isSelected: Boolean)
    suspend fun updateAllMoveOperationsSelection(sessionId: String, isSelected: Boolean)
    suspend fun markMoveOperationExecuted(sessionId: String, operationId: String, success: Boolean, result: String)
    suspend fun cleanupOldData(daysToKeep: Int = 30)
}

class CategorizationStateRepositoryImpl(
    private val dao: CategorizationDao
) : CategorizationStateRepository {
    
    override suspend fun createSession(directoryPath: String): String {
        val sessionId = UUID.randomUUID().toString()
        val session = CategorizationSessionEntity(
            sessionId = sessionId,
            directoryPath = directoryPath,
            totalFilesScanned = 0,
            totalFilesCategorized = 0,
            totalFilesMovedSuccessfully = 0,
            totalFilesMovesFailed = 0,
            isCompleted = false
        )
        dao.insertSession(session)
        return sessionId
    }
    
    override suspend fun getLastSession(): CategorizationSessionEntity? {
        return dao.getLastSession()
    }
    
    override suspend fun updateSessionStats(
        sessionId: String, 
        scanned: Int, 
        categorized: Int, 
        moved: Int, 
        failed: Int
    ) {
        val session = dao.getSession(sessionId)
        if (session != null) {
            val updatedSession = session.copy(
                totalFilesScanned = scanned,
                totalFilesCategorized = categorized,
                totalFilesMovedSuccessfully = moved,
                totalFilesMovesFailed = failed,
                lastAccessedAt = System.currentTimeMillis()
            )
            dao.updateSession(updatedSession)
        }
    }
    
    override suspend fun saveCategorizationResults(sessionId: String, results: List<CategorizationResult>) {
        val entities = results.map { CategorizationResultEntity.fromDomainModel(it, sessionId) }
        dao.insertCategorizationResults(entities)
    }
    
    override suspend fun saveMoveOperations(sessionId: String, operations: List<MoveOperation>) {
        val entities = operations.mapIndexed { index, operation ->
            MoveOperationEntity.fromDomainModel(
                operation, 
                sessionId, 
                id = "${sessionId}_${index}"
            )
        }
        dao.insertMoveOperations(entities)
    }
    
    override suspend fun getCategorizationResults(sessionId: String): Flow<List<CategorizationResult>> {
        return dao.getCategorizationResultsForSession(sessionId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun getMoveOperations(sessionId: String): Flow<List<MoveOperation>> {
        return dao.getMoveOperationsForSession(sessionId).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }
    
    override suspend fun updateMoveOperationSelection(sessionId: String, operationIndex: Int, isSelected: Boolean) {
        val operationId = "${sessionId}_${operationIndex}"
        dao.updateMoveOperationSelection(operationId, isSelected)
    }
    
    override suspend fun updateAllMoveOperationsSelection(sessionId: String, isSelected: Boolean) {
        dao.updateAllMoveOperationsSelection(sessionId, isSelected)
    }
    
    override suspend fun markMoveOperationExecuted(sessionId: String, operationId: String, success: Boolean, result: String) {
        dao.updateMoveOperationExecution(
            operationId = operationId,
            isExecuted = true,
            executedAt = System.currentTimeMillis(),
            result = if (success) "success" else result
        )
    }
    
    override suspend fun cleanupOldData(daysToKeep: Int) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        dao.deleteOldSessions(cutoffTime)
        dao.deleteOldCategorizationResults(cutoffTime)
        dao.deleteOldMoveOperations(cutoffTime)
    }
}
