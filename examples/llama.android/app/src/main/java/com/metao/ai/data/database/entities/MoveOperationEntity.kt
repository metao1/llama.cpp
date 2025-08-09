package com.metao.ai.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.metao.ai.domain.model.MoveOperation
import com.metao.ai.domain.model.FileItem

@Entity(tableName = "move_operations")
data class MoveOperationEntity(
    @PrimaryKey val id: String, // Unique ID for the operation
    val sourceFilePath: String,
    val targetDirectoryPath: String,
    val categoryName: String,
    val fileName: String,
    val isSelected: Boolean,
    val confidence: Float,
    val reasoning: String,
    val sessionId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isExecuted: Boolean = false,
    val executedAt: Long? = null,
    val executionResult: String? = null // "success", "failed", or error message
) {
    fun toDomainModel(): MoveOperation {
        // We need to reconstruct the FileItem from the stored data
        // For now, create a minimal FileItem - in a real implementation,
        // you might want to store more FileItem data or reference it differently
        val fileItem = FileItem(
            file = java.io.File(sourceFilePath),
            name = fileName,
            path = sourceFilePath,
            sizeBytes = 0L, // Would need to be stored separately
            extension = fileName.substringAfterLast(".", ""),
            mimeType = "", // Would need to be stored separately
            lastModified = java.util.Date(),
            isDirectory = false,
            contentPreview = null
        )

        return MoveOperation(
            fileItem = fileItem,
            fromPath = sourceFilePath,
            toPath = "$targetDirectoryPath/$fileName",
            categoryName = categoryName,
            isSelected = isSelected,
            confidence = confidence,
            reasoning = reasoning
        )
    }

    companion object {
        fun fromDomainModel(
            operation: MoveOperation,
            sessionId: String,
            id: String = "${operation.fromPath}_${System.currentTimeMillis()}"
        ): MoveOperationEntity {
            return MoveOperationEntity(
                id = id,
                sourceFilePath = operation.fromPath,
                targetDirectoryPath = operation.toPath.substringBeforeLast("/"),
                categoryName = operation.categoryName,
                fileName = operation.fileItem.name,
                isSelected = operation.isSelected,
                confidence = operation.confidence,
                reasoning = operation.reasoning,
                sessionId = sessionId
            )
        }
    }
}
