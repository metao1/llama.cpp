package com.metao.ai.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categorization_sessions")
data class CategorizationSessionEntity(
    @PrimaryKey val sessionId: String,
    val directoryPath: String,
    val totalFilesScanned: Int,
    val totalFilesCategorized: Int,
    val totalFilesMovedSuccessfully: Int,
    val totalFilesMovesFailed: Int,
    val isCompleted: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val lastAccessedAt: Long = System.currentTimeMillis()
)
