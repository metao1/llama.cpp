package com.metao.ai.domain.model

data class FileCategory(
    val id: String,
    val name: String,
    val description: String,
    val keywords: List<String> = emptyList(),
    val fileTypes: List<FileType> = emptyList(),
    val color: String = "#6200EE", // Material Design primary color
    val isDefault: Boolean = false,
    val folderPath: String? = null,
) {
    companion object {
        fun getDefaultCategories(): List<FileCategory> =
            listOf(
                FileCategory(
                    id = "receipts",
                    name = "Receipts",
                    description = "Purchase receipts, invoices, and transaction records",
                    keywords = listOf("receipt", "invoice", "purchase", "payment", "transaction", "bill"),
                    fileTypes = listOf(FileType.DOCUMENT, FileType.IMAGE),
                    color = "#4CAF50",
                    isDefault = true,
                ),
                FileCategory(
                    id = "work",
                    name = "Work",
                    description = "Work-related documents, presentations, and files",
                    keywords = listOf("work", "office", "business", "meeting", "project", "report", "presentation"),
                    fileTypes = listOf(FileType.DOCUMENT, FileType.PRESENTATION, FileType.SPREADSHEET),
                    color = "#2196F3",
                    isDefault = true,
                ),
                FileCategory(
                    id = "id_docs",
                    name = "ID Documents",
                    description = "Identity documents, licenses, certificates",
                    keywords = listOf("id", "license", "passport", "certificate", "identity", "driver", "social security"),
                    fileTypes = listOf(FileType.DOCUMENT, FileType.IMAGE),
                    color = "#FF9800",
                    isDefault = true,
                ),
                FileCategory(
                    id = "personal",
                    name = "Personal",
                    description = "Personal photos, documents, and files",
                    keywords = listOf("personal", "family", "photo", "vacation", "birthday", "wedding"),
                    fileTypes = listOf(FileType.IMAGE, FileType.VIDEO, FileType.DOCUMENT),
                    color = "#E91E63",
                    isDefault = true,
                ),
                FileCategory(
                    id = "downloads",
                    name = "Downloads",
                    description = "Downloaded files, installers, and temporary files",
                    keywords = listOf("download", "installer", "setup", "temp", "cache"),
                    fileTypes = listOf(FileType.ARCHIVE, FileType.OTHER),
                    color = "#9C27B0",
                    isDefault = true,
                ),
                FileCategory(
                    id = "media",
                    name = "Media",
                    description = "Entertainment media files",
                    keywords = listOf("movie", "music", "song", "video", "entertainment", "media"),
                    fileTypes = listOf(FileType.VIDEO, FileType.AUDIO),
                    color = "#F44336",
                    isDefault = true,
                ),
                FileCategory(
                    id = "books",
                    name = "Books",
                    description = "E-books, novels, textbooks, research papers, and reading materials",
                    keywords = listOf("book", "ebook", "novel", "manual", "guide", "paper", "reading", "pdf", "epub", "mobi", "literature"),
                    fileTypes = listOf(FileType.DOCUMENT),
                    color = "#009688",
                    isDefault = true,
                ),
                FileCategory(
                    id = "documents",
                    name = "Documents",
                    description = "General documents, PDFs, text files, and office files",
                    keywords = listOf("document", "pdf", "text", "word", "excel", "powerpoint", "office"),
                    fileTypes = listOf(FileType.DOCUMENT),
                    color = "#795548",
                    isDefault = true,
                ),
                FileCategory(
                    id = "to_delete",
                    name = "To Delete",
                    description = "Files that can be safely deleted",
                    keywords = listOf("temp", "cache", "old", "duplicate", "trash", "junk"),
                    fileTypes = listOf(FileType.OTHER),
                    color = "#607D8B",
                    isDefault = true,
                ),
            )

        fun fromDynamicName(
            rawName: String,
            description: String = "",
        ): FileCategory {
            val cleanName = rawName.trim().removeSurrounding("\"").removeSurrounding("'")
            val id = cleanName.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

            // Check if it maps to any known standard category definition
            val defaultMatch =
                getDefaultCategories().find {
                    it.id == id || it.name.equals(cleanName, ignoreCase = true) ||
                        it.keywords.any { kw -> cleanName.lowercase().contains(kw) }
                }

            if (defaultMatch != null) {
                return defaultMatch.copy(
                    name = cleanName.ifBlank { defaultMatch.name },
                    description = description.ifBlank { defaultMatch.description },
                )
            }

            // Generate a color for dynamic category
            val colorHue = ((id.hashCode() and 0x7FFFFFFF) % 360).toFloat()
            val hsv = floatArrayOf(colorHue, 0.65f, 0.85f)
            val dynamicColorHex = String.format("#%06X", 0xFFFFFF and android.graphics.Color.HSVToColor(hsv))

            val formattedName =
                cleanName.ifBlank { "Uncategorized" }
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }

            return FileCategory(
                id = id.ifBlank { "custom_${System.currentTimeMillis()}" },
                name = formattedName,
                description = description.ifBlank { "Dynamically determined category by LLM model" },
                color = dynamicColorHex,
                isDefault = false,
            )
        }
    }
}
