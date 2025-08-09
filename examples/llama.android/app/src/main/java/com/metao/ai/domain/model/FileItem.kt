package com.metao.ai.domain.model

import java.io.File
import java.util.Date

data class FileItem(
    val file: File,
    val name: String = file.name,
    val path: String = file.absolutePath,
    val sizeBytes: Long = if (file.exists()) file.length() else 0L,
    val extension: String = file.extension.lowercase(),
    val mimeType: String? = null,
    val lastModified: Date = Date(if (file.exists()) file.lastModified() else 0L),
    val isDirectory: Boolean = file.isDirectory,
    val contentPreview: String? = null, // For small text files
    val suggestedCategory: String? = null,
    val confidence: Float = 0f
) {
    val sizeFormatted: String
        get() = formatFileSize(sizeBytes)

    val fileType: FileType
        get() = FileType.fromExtension(extension)

    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.1f GB".format(gb)
    }
}

enum class FileType(val displayName: String, val extensions: Set<String>) {
    DOCUMENT("Document", setOf("pdf", "doc", "docx", "txt", "rtf", "odt")),
    IMAGE("Image", setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg")),
    VIDEO("Video", setOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm")),
    AUDIO("Audio", setOf("mp3", "wav", "flac", "aac", "ogg", "m4a")),
    ARCHIVE("Archive", setOf("zip", "rar", "7z", "tar", "gz", "bz2")),
    SPREADSHEET("Spreadsheet", setOf("xls", "xlsx", "csv", "ods")),
    PRESENTATION("Presentation", setOf("ppt", "pptx", "odp")),
    CODE("Code", setOf("java", "kt", "py", "js", "html", "css", "cpp", "c", "h")),
    OTHER("Other", emptySet());

    companion object {
        fun fromExtension(extension: String): FileType {
            return FileType.entries.find { it.extensions.contains(extension.lowercase()) } ?: OTHER
        }
    }
}
