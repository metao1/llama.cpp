package com.metao.ai.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.model.FileCategory
import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.FileType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Date

@Entity(tableName = "categorization_results")
@TypeConverters(CategorizationResultConverters::class)
data class CategorizationResultEntity(
    @PrimaryKey val filePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val extension: String,
    val mimeType: String?,
    val lastModified: Long,
    val isDirectory: Boolean,
    val contentPreview: String?,
    val suggestedCategoryId: String,
    val suggestedCategoryName: String,
    val suggestedCategoryDescription: String,
    val suggestedCategoryKeywords: String, // JSON string
    val suggestedCategoryFileTypes: String, // JSON string
    val suggestedCategoryColor: String,
    val confidence: Float,
    val reasoning: String,
    val isConfirmed: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val sessionId: String? = null
) {
    fun toDomainModel(): CategorizationResult {
        val fileItem = FileItem(
            file = File(filePath),
            name = fileName,
            path = filePath,
            sizeBytes = sizeBytes,
            extension = extension,
            mimeType = mimeType,
            lastModified = Date(lastModified),
            isDirectory = isDirectory,
            contentPreview = contentPreview
        )

        val category = FileCategory(
            id = suggestedCategoryId,
            name = suggestedCategoryName,
            description = suggestedCategoryDescription,
            keywords = Json.decodeFromString<List<String>>(suggestedCategoryKeywords),
            fileTypes = Json.decodeFromString<List<FileType>>(suggestedCategoryFileTypes),
            color = suggestedCategoryColor,
            isDefault = false
        )

        return CategorizationResult(
            fileItem = fileItem,
            suggestedCategory = category,
            confidence = confidence,
            reasoning = reasoning,
            isConfirmed = isConfirmed
        )
    }

    companion object {
        fun fromDomainModel(result: CategorizationResult, sessionId: String? = null): CategorizationResultEntity {
            return CategorizationResultEntity(
                filePath = result.fileItem.path,
                fileName = result.fileItem.name,
                sizeBytes = result.fileItem.sizeBytes,
                extension = result.fileItem.extension,
                mimeType = result.fileItem.mimeType,
                lastModified = result.fileItem.lastModified.time,
                isDirectory = result.fileItem.isDirectory,
                contentPreview = result.fileItem.contentPreview,
                suggestedCategoryId = result.suggestedCategory.id,
                suggestedCategoryName = result.suggestedCategory.name,
                suggestedCategoryDescription = result.suggestedCategory.description,
                suggestedCategoryKeywords = Json.encodeToString(result.suggestedCategory.keywords),
                suggestedCategoryFileTypes = Json.encodeToString(result.suggestedCategory.fileTypes),
                suggestedCategoryColor = result.suggestedCategory.color,
                confidence = result.confidence,
                reasoning = result.reasoning,
                isConfirmed = result.isConfirmed,
                sessionId = sessionId
            )
        }
    }
}

class CategorizationResultConverters {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromFileTypeList(value: List<FileType>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toFileTypeList(value: String): List<FileType> {
        return Json.decodeFromString(value)
    }
}
