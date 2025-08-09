package com.metao.ai.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.metao.ai.domain.model.ModelInfo
import android.net.Uri
import java.io.File
import androidx.core.net.toUri

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val description: String,
    val sourceUrl: String,
    val destinationPath: String,
    val sizeBytes: Long,
    val isDownloaded: Boolean,
    val isCustom: Boolean = false,
    val dateAdded: Long = System.currentTimeMillis()
)

// Extension functions to convert between Entity and Domain model
fun ModelEntity.toDomainModel(): ModelInfo {
    return ModelInfo(
        id = id,
        name = name,
        description = description,
        sourceUrl = sourceUrl.toUri(),
        destinationFile = File(destinationPath),
        sizeBytes = sizeBytes,
        isDownloaded = isDownloaded
    )
}

fun ModelInfo.toEntity(isCustom: Boolean = false): ModelEntity {
    return ModelEntity(
        id = id,
        name = name,
        description = description,
        sourceUrl = sourceUrl.toString(),
        destinationPath = destinationFile.absolutePath,
        sizeBytes = sizeBytes,
        isDownloaded = isDownloaded,
        isCustom = isCustom
    )
}
