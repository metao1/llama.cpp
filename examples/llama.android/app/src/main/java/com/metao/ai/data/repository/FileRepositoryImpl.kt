package com.metao.ai.data.repository

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.model.FileCategory
import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.TextGenerationState
import com.metao.ai.domain.repository.FileRepository
import com.metao.ai.domain.usecase.GenerateTextUseCase
import com.metao.ai.domain.usecase.MoveFileProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class FileRepositoryImpl(
    private val context: Context,
) : FileRepository {
    companion object {
        private const val TAG = "FileRepositoryImpl"
        private const val MAX_CONTENT_PREVIEW_SIZE = 500 // characters
    }

    override suspend fun scanDirectory(
        directoryPath: String,
        includeSubdirectories: Boolean,
        maxFileSizeForContent: Long,
    ): Flow<List<FileItem>> =
        flow {
            Log.d(TAG, "Starting directory scan: $directoryPath")
            val directory = File(directoryPath)
            if (!directory.exists() || !directory.isDirectory) {
                Log.e(TAG, "Directory does not exist or is not a directory: $directoryPath")
                Log.e(TAG, "Directory exists: ${directory.exists()}, isDirectory: ${directory.isDirectory}")
                emit(emptyList())
                return@flow
            }

            val fileItems = mutableListOf<FileItem>()

            try {
                var files =
                    if (includeSubdirectories) {
                        Log.d(TAG, "Starting ultra-light recursive scan...")
                        try {
                            directory.walkTopDown()
                                .maxDepth(4)
                                .onEnter { dir ->
                                    val name = dir.name.lowercase()
                                    name != "android" && name != "obb" && name != "cache" && !name.startsWith(".") && !name.startsWith("whatsapp")
                                }
                                .asSequence()
                                .filter { file ->
                                    file.isFile && !file.name.startsWith(".") && (file.canRead() || file.length() > 0)
                                }
                                .toList()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error walking directory", e)
                            emptyList()
                        }
                    } else {
                        val allItems = directory.listFiles() ?: emptyArray()
                        Log.d(TAG, "Total items in directory: ${allItems.size}")

                        allItems.filter { file ->
                            file.isFile && !file.name.startsWith(".") && (file.canRead() || file.length() > 0)
                        }
                    }

                if (files.isEmpty()) {
                    Log.d(TAG, "Java File API returned 0 files, trying MediaStore query for $directoryPath...")
                    files = queryMediaStoreForDirectory(context, directoryPath)
                }

                Log.d(TAG, "Found ${files.size} files in directory: $directoryPath")

                // Process files in batches and emit progress
                val batchSize = 100
                files.chunked(batchSize).forEachIndexed { batchIndex, batch ->
                    batch.forEach { file ->
                        try {
                            val fileItem = createFileItem(file, maxFileSizeForContent)
                            fileItems.add(fileItem)

                            if (includeSubdirectories && fileItems.size % 50 == 0) {
                                Log.d(TAG, "Processed ${fileItems.size} files so far...")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error processing file: ${file.absolutePath}", e)
                        }
                    }

                    // Emit intermediate results for large scans
                    if (includeSubdirectories && batchIndex % 5 == 0 && fileItems.isNotEmpty()) {
                        Log.d(TAG, "Emitting intermediate results: ${fileItems.size} files")
                        emit(fileItems.toList()) // Emit a copy
                    }
                }

                Log.d(TAG, "Emitting final ${fileItems.size} file items")
                emit(fileItems)
            } catch (e: Exception) {
                Log.e(TAG, "Error scanning directory: $directoryPath", e)
                emit(emptyList())
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun getFileMetadata(
        filePath: String,
        maxContentSize: Long,
    ): FileItem? =
        try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                createFileItem(file, maxContentSize)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file metadata: $filePath", e)
            null
        }

    private fun createFileItem(
        file: File,
        maxContentSize: Long,
    ): FileItem {
        val mimeType = getMimeType(file.extension)
        val contentPreview =
            if (file.length() <= maxContentSize) {
                getContentPreview(file)
            } else {
                null
            }

        return FileItem(
            file = file,
            mimeType = mimeType,
            contentPreview = contentPreview,
        )
    }

    private fun getMimeType(extension: String): String? = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())

    private fun getContentPreview(file: File): String? =
        try {
            when {
                file.extension.lowercase() in setOf("txt", "md", "log", "csv") -> {
                    file.readText().take(MAX_CONTENT_PREVIEW_SIZE)
                }
                file.extension.lowercase() in setOf("pdf") -> {
                    // For PDF files, we can't easily extract text without additional libraries
                    // Return filename-based info for now
                    "PDF document: ${file.nameWithoutExtension}"
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error reading file content: ${file.absolutePath}", e)
            null
        }

    override suspend fun categorizeFile(
        fileItem: FileItem,
        availableCategories: List<FileCategory>,
        generateTextUseCase: GenerateTextUseCase,
    ): Flow<CategorizationResult> =
        flow {
            Log.d(TAG, "Starting AI categorization for file: ${fileItem.name}")

            try {
                val prompt = buildCategorizationPrompt(fileItem, availableCategories)
                Log.d(TAG, "Generated prompt for ${fileItem.name}: $prompt")

                var response = ""
                var hasCompleted = false
                var tokenCount = 0

                // Use timeout to prevent hanging
                withTimeoutOrNull(30000.milliseconds) {
                    // 30 second timeout
                    generateTextUseCase(prompt, useChat = false).collect { state ->
                        Log.d(TAG, "AI state for ${fileItem.name}: $state")
                        when (state) {
                            is TextGenerationState.TokenGenerated -> {
                                response += state.token
                                tokenCount++
                                Log.v(TAG, "Token #$tokenCount received: '${state.token}' (total response length: ${response.length})")

                                // Check if we have enough content to parse (at least CATEGORY and CONFIDENCE)
                                if (tokenCount > 10 &&
                                    (
                                        response.contains("CONFIDENCE:", ignoreCase = true) ||
                                            response.contains("REASONING:", ignoreCase = true)
                                    )
                                ) {
                                    Log.d(TAG, "Sufficient response received for ${fileItem.name}, attempting to parse early")
                                    hasCompleted = true
                                    val result = parseCategorizationResponse(response, fileItem, availableCategories)
                                    Log.d(
                                        TAG,
                                        "Early parsed result for ${fileItem.name}: category=${result.suggestedCategory.name}, confidence=${result.confidence}",
                                    )
                                    emit(result)
                                    return@collect
                                }
                            }
                            is TextGenerationState.Completed -> {
                                hasCompleted = true
                                Log.d(TAG, "AI response completed for ${fileItem.name}: $response")
                                val result = parseCategorizationResponse(response, fileItem, availableCategories)
                                Log.d(
                                    TAG,
                                    "Parsed result for ${fileItem.name}: category=${result.suggestedCategory.name}, confidence=${result.confidence}",
                                )
                                emit(result)
                            }
                            is TextGenerationState.Failed -> {
                                Log.e(TAG, "AI generation failed for ${fileItem.name}: ${state.error}")
                                throw Exception(state.error)
                            }
                            is TextGenerationState.Loading -> {
                                Log.d(TAG, "AI model loading for ${fileItem.name}")
                            }
                            else -> {
                                Log.d(TAG, "Other AI state for ${fileItem.name}: $state")
                            }
                        }
                    }
                }

                // If we didn't get a completion, try to parse what we have or use fallback
                if (!hasCompleted) {
                    if (response.isNotEmpty() && response.contains("CATEGORY:", ignoreCase = true)) {
                        Log.w(TAG, "AI categorization timed out for ${fileItem.name}, but got partial response: $response")
                        val result = parseCategorizationResponse(response, fileItem, availableCategories)
                        emit(result)
                    } else {
                        Log.w(TAG, "AI categorization failed for ${fileItem.name}, using rule-based fallback. Response was: '$response'")
                        val fallbackResult = performRuleBasedCategorization(fileItem, availableCategories)
                        emit(fallbackResult.copy(reasoning = "AI timeout or no response. Response: '$response'"))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during AI categorization for ${fileItem.name}", e)
                // Fallback to rule-based categorization
                val fallbackResult = performRuleBasedCategorization(fileItem, availableCategories)
                fallbackResult.copy(reasoning = "AI categorization failed: ${e.message}. Used rule-based fallback.")
                emit(fallbackResult)
            }
        }.flowOn(Dispatchers.IO)

    private fun buildCategorizationPrompt(
        fileItem: FileItem,
        availableCategories: List<FileCategory>,
    ): String {
        val referenceExamples = availableCategories.take(6).joinToString(", ") { it.name }
        val previewSnippet = fileItem.contentPreview?.let { "\nContent Snippet: \"$it\"" } ?: ""

        val userPrompt =
            """
You are an intelligent file organization assistant.
Analyze the following file and provide its category name, confidence score between 0.0 and 1.0, and a brief reasoning.

File Details:
- Name: ${fileItem.name}
- Path: ${fileItem.path}
- Type: ${fileItem.fileType.displayName}$previewSnippet

Examples of categories: $referenceExamples (or any other appropriate category name).

Give your response in this exact format without any brackets:
CATEGORY: Documents
CONFIDENCE: 0.9
REASONING: General document file.
            """.trimIndent()

        return "<start_of_turn>user\n$userPrompt<end_of_turn>"
    }

    private fun parseCategorizationResponse(
        response: String,
        fileItem: FileItem,
        availableCategories: List<FileCategory>,
    ): CategorizationResult =
        try {
            Log.d(TAG, "Parsing AI response for ${fileItem.name}: $response")

            // Clean response
            val cleanResponse = response.replace(Regex("<[^>]*>"), "").trim()

            // Regex extraction for flexibility against chatty LLM outputs
            val categoryMatch =
                Regex("CATEGORY\\s*[:\\-]\\s*([a-zA-Z0-9\\s&\\-_]+)", RegexOption.IGNORE_CASE).find(
                    cleanResponse,
                )
            val confidenceMatch =
                Regex("CONFIDENCE\\s*[:\\-]\\s*([0-9]*\\.?[0-9]+)", RegexOption.IGNORE_CASE).find(
                    cleanResponse,
                )
            val reasoningMatch =
                Regex("REASONING\\s*[:\\-]\\s*([^\\n]+)", RegexOption.IGNORE_CASE).find(
                    cleanResponse,
                )

            val categoryRawName =
                categoryMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf {
                    !it.startsWith("[")
                } ?: extractCategoryFallback(fileItem)

            val confidenceStr = confidenceMatch?.groupValues?.getOrNull(1)?.trim()
            val extractedNum = confidenceStr?.toFloatOrNull()
            val confidence =
                when {
                    extractedNum != null -> {
                        if (extractedNum > 1.0f && extractedNum <= 100f) extractedNum / 100f else extractedNum
                    }
                    confidenceStr?.contains("high", ignoreCase = true) == true -> 0.9f
                    confidenceStr?.contains("medium", ignoreCase = true) == true -> 0.7f
                    confidenceStr?.contains("low", ignoreCase = true) == true -> 0.5f
                    else -> 0.85f
                }.coerceIn(0.1f, 1.0f)

            val reasoning =
                reasoningMatch?.groupValues?.getOrNull(1)?.trim()
                    ?: "🤖 LLM categorized based on file analysis"

            val dynamicCategory = FileCategory.fromDynamicName(categoryRawName, reasoning)
            Log.d(
                TAG,
                "Successfully parsed AI category '${dynamicCategory.name}' (confidence=$confidence) for ${fileItem.name}",
            )

            CategorizationResult(
                fileItem = fileItem,
                suggestedCategory = dynamicCategory,
                confidence = confidence,
                reasoning = reasoning,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing categorization response for ${fileItem.name}", e)
            val fallbackResult = performRuleBasedCategorization(fileItem, availableCategories)
            fallbackResult.copy(reasoning = "Failed to parse AI response: ${e.message}")
        }

    private fun extractCategoryFallback(fileItem: FileItem): String {
        val name = fileItem.name.lowercase()
        val path = fileItem.path.lowercase()
        val ext = fileItem.extension.lowercase()
        return when {
            path.contains("/books") || ext in listOf("epub", "mobi", "azw3", "pdf") || name.contains("book") || name.contains("edition") -> "Books"
            name.contains("receipt") || name.contains("invoice") -> "Receipts"
            name.contains("work") || name.contains("report") -> "Work"
            name.contains("id") || name.contains("passport") -> "ID Documents"
            ext in listOf("jpg", "png", "mp4") -> "Media"
            else -> "Documents"
        }
    }

    private fun performRuleBasedCategorization(
        fileItem: FileItem,
        availableCategories: List<FileCategory>,
    ): CategorizationResult {
        val fileName = fileItem.name.lowercase()
        val path = fileItem.path.lowercase()
        val ext = fileItem.extension.lowercase()

        val category =
            when {
                path.contains("/books") || ext in listOf("epub", "mobi", "azw3", "fb2", "djvu") ||
                    fileName.contains("book") || fileName.contains("novel") || fileName.contains("manual") || fileName.contains("guide") -> {
                    availableCategories.find { it.id == "books" }
                }
                fileName.contains("receipt") || fileName.contains("invoice") || fileName.contains("bill") -> {
                    availableCategories.find { it.id == "receipts" }
                }
                fileName.contains("work") || fileName.contains("office") || fileName.contains("business") || fileName.contains("meeting") -> {
                    availableCategories.find { it.id == "work" }
                }
                fileName.contains("passport") || fileName.contains("license") || fileName.contains("id_") -> {
                    availableCategories.find { it.id == "id_docs" }
                }
                ext in listOf("pdf", "doc", "docx", "txt", "xlsx", "pptx") -> {
                    availableCategories.find { it.id == "documents" }
                }
                ext in listOf("jpg", "jpeg", "png", "mp4", "mp3", "mkv") -> {
                    availableCategories.find { it.id == "media" }
                }
                else -> {
                    availableCategories.find { it.id == "downloads" }
                }
            } ?: availableCategories.find { it.id == "books" } ?: availableCategories.firstOrNull() ?: FileCategory.getDefaultCategories().first()

        return CategorizationResult(
            fileItem = fileItem,
            suggestedCategory = category,
            confidence = 0.85f,
            reasoning = "📁 Context & extension rule: ${fileItem.name} → ${category.name}",
        )
    }

    override suspend fun moveFiles(
        results: List<CategorizationResult>,
        baseDirectory: String,
    ): Flow<MoveFileProgress> =
        flow {
            val confirmedResults = results.filter { it.isConfirmed }
            var processedCount = 0

            for (result in confirmedResults) {
                try {
                    val progress = processedCount.toFloat() / confirmedResults.size
                    emit(MoveFileProgress.Moving(progress, result.fileItem.name))

                    val categoryFolder = File(baseDirectory, result.suggestedCategory.name)
                    if (!categoryFolder.exists()) {
                        categoryFolder.mkdirs()
                    }

                    val sourceFile = result.fileItem.file
                    val destinationFile = File(categoryFolder, sourceFile.name)

                    // Handle file name conflicts
                    val finalDestination =
                        if (destinationFile.exists()) {
                            generateUniqueFileName(destinationFile)
                        } else {
                            destinationFile
                        }

                    if (sourceFile.renameTo(finalDestination)) {
                        emit(
                            MoveFileProgress.FileMovedSuccessfully(
                                sourceFile.name,
                                finalDestination.absolutePath,
                            ),
                        )
                    } else {
                        emit(
                            MoveFileProgress.FileMoveError(
                                sourceFile.name,
                                "Failed to move file",
                            ),
                        )
                    }

                    processedCount++
                } catch (e: Exception) {
                    Log.e(TAG, "Error moving file: ${result.fileItem.name}", e)
                    emit(
                        MoveFileProgress.FileMoveError(
                            result.fileItem.name,
                            e.message ?: "Unknown error",
                        ),
                    )
                }
            }

            emit(MoveFileProgress.AllFilesProcessed)
        }.flowOn(Dispatchers.IO)

    private fun generateUniqueFileName(file: File): File {
        val nameWithoutExt = file.nameWithoutExtension
        val extension = file.extension
        var counter = 1

        while (true) {
            val newName =
                if (extension.isNotEmpty()) {
                    "${nameWithoutExt}_$counter.$extension"
                } else {
                    "${nameWithoutExt}_$counter"
                }
            val newFile = File(file.parent, newName)
            if (!newFile.exists()) {
                return newFile
            }
            counter++
        }
    }

    override suspend fun createCategoryFolders(
        categories: List<FileCategory>,
        baseDirectory: String,
    ): Boolean =
        try {
            val baseDir = File(baseDirectory)
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }

            categories.forEach { category ->
                val categoryDir = File(baseDir, category.name)
                if (!categoryDir.exists()) {
                    categoryDir.mkdirs()
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating category folders", e)
            false
        }

    override suspend fun getAvailableDirectories(): List<String> {
        val directories = mutableListOf<String>()

        try {
            // Add external storage directories
            Environment.getExternalStorageDirectory()?.let {
                directories.add(it.absolutePath)
            }

            // Add Downloads folder
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let {
                directories.add(it.absolutePath)
            }

            // Add Documents folder
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.let {
                directories.add(it.absolutePath)
            }

            // Add app-specific external directories
            context.getExternalFilesDirs(null)?.forEach { dir ->
                dir?.let { directories.add(it.absolutePath) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting available directories", e)
        }

        return directories.distinct()
    }

    override suspend fun isDirectoryAccessible(directoryPath: String): Boolean =
        try {
            val directory = File(directoryPath)
            directory.exists() && directory.isDirectory && directory.canRead()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking directory accessibility: $directoryPath", e)
            false
        }

    private fun queryMediaStoreForDirectory(
        context: Context,
        directoryPath: String,
    ): List<File> {
        val files = mutableListOf<File>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
        val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ?"
        val selectionArgs = arrayOf("$directoryPath/%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val dataColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
                if (dataColumn != -1) {
                    while (cursor.moveToNext() && files.size < 150) {
                        val path = cursor.getString(dataColumn)
                        if (!path.isNullOrEmpty()) {
                            val file = File(path)
                            if (file.exists() && file.isFile && !file.name.startsWith(".")) {
                                files.add(file)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore for $directoryPath", e)
        }
        Log.d(TAG, "MediaStore query found ${files.size} files for $directoryPath")
        return files
    }
}
