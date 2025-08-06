package com.metao.ai.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import android.webkit.MimeTypeMap
import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.FileCategory
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.model.TextGenerationState
import com.metao.ai.domain.repository.FileRepository
import com.metao.ai.domain.usecase.GenerateTextUseCase
import com.metao.ai.domain.usecase.MoveFileProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.util.Date

class FileRepositoryImpl(
    private val context: Context
) : FileRepository {

    companion object {
        private const val TAG = "FileRepositoryImpl"
        private const val MAX_CONTENT_PREVIEW_SIZE = 500 // characters
    }

    override suspend fun scanDirectory(
        directoryPath: String,
        includeSubdirectories: Boolean,
        maxFileSizeForContent: Long
    ): Flow<List<FileItem>> = flow {
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
            val files = if (includeSubdirectories) {
                Log.d(TAG, "Starting recursive scan...")
                val allFiles = directory.walkTopDown().toList()
                Log.d(TAG, "Total items found recursively: ${allFiles.size}")

                val filteredFiles = allFiles
                    .filter { file ->
                        val isFile = file.isFile
                        val isNotHidden = !file.name.startsWith(".")
                        val isReadable = file.canRead()

                        if (file.parent == directoryPath) { // Log only direct children for clarity
                            Log.d(TAG, "File: ${file.name} - isFile: $isFile, notHidden: $isNotHidden, readable: $isReadable, size: ${file.length()}")
                        }

                        // Show all readable items (files AND directories)
                        isReadable
                    }
                    .take(10000) // Limit to prevent memory issues

                Log.d(TAG, "Files after recursive filtering: ${filteredFiles.size}")
                filteredFiles
            } else {
                val allItems = directory.listFiles() ?: emptyArray()
                Log.d(TAG, "Total items in directory: ${allItems.size}")

                val filteredFiles = allItems.filter { file ->
                    val isFile = file.isFile
                    val isNotHidden = !file.name.startsWith(".")
                    val isReadable = file.canRead()

                    Log.d(TAG, "File: ${file.name} - isFile: $isFile, notHidden: $isNotHidden, readable: $isReadable, size: ${file.length()}")

                    // Show all readable items (files AND directories)
                    isReadable
                }

                Log.d(TAG, "Files after filtering: ${filteredFiles.size}")
                filteredFiles
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

    override suspend fun getFileMetadata(filePath: String, maxContentSize: Long): FileItem? {
        return try {
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                createFileItem(file, maxContentSize)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting file metadata: $filePath", e)
            null
        }
    }

    private fun createFileItem(file: File, maxContentSize: Long): FileItem {
        val mimeType = getMimeType(file.extension)
        val contentPreview = if (file.length() <= maxContentSize) {
            getContentPreview(file)
        } else null

        return FileItem(
            file = file,
            mimeType = mimeType,
            contentPreview = contentPreview
        )
    }

    private fun getMimeType(extension: String): String? {
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
    }

    private fun getContentPreview(file: File): String? {
        return try {
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
    }

    override suspend fun categorizeFile(
        fileItem: FileItem,
        availableCategories: List<FileCategory>,
        generateTextUseCase: GenerateTextUseCase
    ): Flow<CategorizationResult> = flow {
        Log.d(TAG, "Starting AI categorization for file: ${fileItem.name}")

        try {
            val prompt = buildCategorizationPrompt(fileItem, availableCategories)
            Log.d(TAG, "Generated prompt for ${fileItem.name}: $prompt")

            var response = ""
            var hasCompleted = false
            var tokenCount = 0

            // Use timeout to prevent hanging
            withTimeoutOrNull(30000) { // 30 second timeout
                generateTextUseCase(prompt, useChat = false).collect { state ->
                    Log.d(TAG, "AI state for ${fileItem.name}: $state")
                    when (state) {
                        is TextGenerationState.TokenGenerated -> {
                            response += state.token
                            tokenCount++
                            Log.v(TAG, "Token #$tokenCount received: '${state.token}' (total response length: ${response.length})")

                            // Check if we have enough content to parse (at least CATEGORY and CONFIDENCE)
                            if (tokenCount > 10 && (response.contains("CONFIDENCE:", ignoreCase = true) || response.contains("REASONING:", ignoreCase = true))) {
                                Log.d(TAG, "Sufficient response received for ${fileItem.name}, attempting to parse early")
                                hasCompleted = true
                                val result = parseCategorizationResponse(response, fileItem, availableCategories)
                                Log.d(TAG, "Early parsed result for ${fileItem.name}: category=${result.suggestedCategory.name}, confidence=${result.confidence}")
                                emit(result)
                                return@collect
                            }
                        }
                        is TextGenerationState.Completed -> {
                            hasCompleted = true
                            Log.d(TAG, "AI response completed for ${fileItem.name}: $response")
                            val result = parseCategorizationResponse(response, fileItem, availableCategories)
                            Log.d(TAG, "Parsed result for ${fileItem.name}: category=${result.suggestedCategory.name}, confidence=${result.confidence}")
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
        availableCategories: List<FileCategory>
    ): String {
        val categoriesText = availableCategories.joinToString("\n") { category ->
            "${category.id}: ${category.name} - ${category.description}"
        }

        val userPrompt = """
Categorize file: ${fileItem.name}

Categories: $categoriesText

Respond format:
CATEGORY: [id]
CONFIDENCE: [0.0-1.0]
        """.trimIndent()

        // Use proper LLAMA formatting
        return "<start_of_turn>user\n$userPrompt<end_of_turn>"
    }

    private fun parseCategorizationResponse(
        response: String,
        fileItem: FileItem,
        availableCategories: List<FileCategory>
    ): CategorizationResult {
        return try {
            Log.d(TAG, "Parsing AI response for ${fileItem.name}: $response")

            // Clean the response by removing LLAMA formatting tokens
            val cleanResponse = response.replace(Regex("<[^>]*>"), "").trim()
            Log.d(TAG, "Cleaned response: $cleanResponse")

            val lines = cleanResponse.lines().map { it.trim() }.filter { it.isNotEmpty() }

            // Find the required fields with more flexible parsing
            var categoryId: String? = null
            var confidence = 0.5f
            var reasoning = ""

            for (line in lines) {
                when {
                    line.startsWith("CATEGORY:", ignoreCase = true) -> {
                        categoryId = line.substringAfter(":").trim()
                        Log.d(TAG, "Found category: $categoryId")
                    }
                    line.startsWith("CONFIDENCE:", ignoreCase = true) -> {
                        val confidenceStr = line.substringAfter(":").trim()
                        confidence = confidenceStr.toFloatOrNull() ?: run {
                            Log.w(TAG, "Could not parse confidence '$confidenceStr', using default 0.5")
                            0.5f
                        }
                        Log.d(TAG, "Found confidence: $confidence (from '$confidenceStr')")
                    }
                    line.startsWith("REASONING:", ignoreCase = true) -> {
                        reasoning = line.substringAfter(":").trim()
                        Log.d(TAG, "Found reasoning: $reasoning")
                    }
                }
            }

            // Find the category or use fallback
            val category = if (categoryId != null) {
                availableCategories.find { it.id.equals(categoryId, ignoreCase = true) }
                    ?: availableCategories.find { it.name.equals(categoryId, ignoreCase = true) }
            } else null

            if (category != null) {
                // If confidence is 0, assign a reasonable default based on category match
                val finalConfidence = if (confidence <= 0f) {
                    when {
                        categoryId?.equals(category.id, ignoreCase = true) == true -> 0.8f
                        categoryId?.equals(category.name, ignoreCase = true) == true -> 0.7f
                        else -> 0.6f
                    }
                } else {
                    confidence.coerceIn(0f, 1f)
                }

                Log.d(TAG, "Successfully parsed AI categorization for ${fileItem.name}: ${category.name} (confidence: $finalConfidence)")
                CategorizationResult(
                    fileItem = fileItem,
                    suggestedCategory = category,
                    confidence = finalConfidence,
                    reasoning = reasoning.ifEmpty { "AI-powered categorization" }
                )
            } else {
                Log.w(TAG, "Could not find matching category for '$categoryId', using fallback")
                val fallbackResult = performRuleBasedCategorization(fileItem, availableCategories)
                fallbackResult.copy(reasoning = "AI response unclear, used rule-based fallback. AI said: $cleanResponse")
            }

        } catch (e: Exception) {
            Log.w(TAG, "Error parsing categorization response for ${fileItem.name}", e)
            val fallbackResult = performRuleBasedCategorization(fileItem, availableCategories)
            fallbackResult.copy(reasoning = "Failed to parse AI response: ${e.message}")
        }
    }

    private fun performRuleBasedCategorization(
        fileItem: FileItem,
        availableCategories: List<FileCategory>
    ): CategorizationResult {
        // Simple rule-based fallback categorization
        val fileName = fileItem.name.lowercase()
        val fileType = fileItem.fileType

        val category = when {
            fileName.contains("receipt") || fileName.contains("invoice") || fileName.contains("bill") -> {
                availableCategories.find { it.id == "receipts" }
            }
            fileName.contains("work") || fileName.contains("office") || fileName.contains("business") -> {
                availableCategories.find { it.id == "work" }
            }
            fileName.contains("id") || fileName.contains("license") || fileName.contains("passport") -> {
                availableCategories.find { it.id == "id_docs" }
            }
            fileType in listOf(com.metao.ai.domain.model.FileType.IMAGE, com.metao.ai.domain.model.FileType.VIDEO) -> {
                availableCategories.find { it.id == "personal" }
            }
            fileType in listOf(com.metao.ai.domain.model.FileType.AUDIO, com.metao.ai.domain.model.FileType.VIDEO) -> {
                availableCategories.find { it.id == "media" }
            }
            else -> {
                availableCategories.find { it.id == "downloads" }
            }
        } ?: availableCategories.firstOrNull() ?: FileCategory.getDefaultCategories().first()

        return CategorizationResult(
            fileItem = fileItem,
            suggestedCategory = category,
            confidence = 0.3f, // Lower confidence for rule-based
            reasoning = "⚠️ Rule-based fallback categorization (AI model not available or failed)"
        )
    }

    override suspend fun moveFiles(
        results: List<CategorizationResult>,
        baseDirectory: String
    ): Flow<MoveFileProgress> = flow {
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
                val finalDestination = if (destinationFile.exists()) {
                    generateUniqueFileName(destinationFile)
                } else {
                    destinationFile
                }

                if (sourceFile.renameTo(finalDestination)) {
                    emit(MoveFileProgress.FileMovedSuccessfully(
                        sourceFile.name,
                        finalDestination.absolutePath
                    ))
                } else {
                    emit(MoveFileProgress.FileMoveError(
                        sourceFile.name,
                        "Failed to move file"
                    ))
                }

                processedCount++
            } catch (e: Exception) {
                Log.e(TAG, "Error moving file: ${result.fileItem.name}", e)
                emit(MoveFileProgress.FileMoveError(
                    result.fileItem.name,
                    e.message ?: "Unknown error"
                ))
            }
        }

        emit(MoveFileProgress.AllFilesProcessed)
    }.flowOn(Dispatchers.IO)

    private fun generateUniqueFileName(file: File): File {
        val nameWithoutExt = file.nameWithoutExtension
        val extension = file.extension
        var counter = 1

        while (true) {
            val newName = if (extension.isNotEmpty()) {
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
        baseDirectory: String
    ): Boolean {
        return try {
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

    override suspend fun isDirectoryAccessible(directoryPath: String): Boolean {
        return try {
            val directory = File(directoryPath)
            directory.exists() && directory.isDirectory && directory.canRead()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking directory accessibility: $directoryPath", e)
            false
        }
    }
}
