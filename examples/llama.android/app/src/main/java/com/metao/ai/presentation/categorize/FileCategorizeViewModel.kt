package com.metao.ai.presentation.categorize

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metao.ai.data.repository.CategorizationStateRepository
import com.metao.ai.domain.manager.ModelStateManager
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.model.CategorizationState
import com.metao.ai.domain.model.FileCategory
import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.MoveOperation
import com.metao.ai.domain.model.MoveReport
import com.metao.ai.domain.usecase.CategorizeFileUseCase
import com.metao.ai.domain.usecase.IsModelLoadedUseCase
import com.metao.ai.domain.usecase.ScanDirectoryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FileCategorizeViewModel(
    private val scanDirectoryUseCase: ScanDirectoryUseCase,
    private val categorizeFileUseCase: CategorizeFileUseCase,
    private val isModelLoadedUseCase: IsModelLoadedUseCase,
    private val modelStateManager: ModelStateManager,
    private val stateRepository: CategorizationStateRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "FileCategorizeViewModel"
    }

    private val _uiState = MutableStateFlow(FileCategorizeUiState())
    val uiState: StateFlow<FileCategorizeUiState> = _uiState.asStateFlow()

    private var currentSessionId: String? = null

    init {
        // Initialize with default categories
        _uiState.update {
            it.copy(availableCategories = FileCategory.getDefaultCategories())
        }

        // Observe model state changes in real-time
        viewModelScope.launch {
            modelStateManager.isModelLoaded.collect { isLoaded ->
                Log.d(TAG, "Model state changed: $isLoaded")
                _uiState.update { it.copy(isModelLoaded = isLoaded) }
            }
        }

        // Initial check
        checkModelStatus()
    }

    private fun checkModelStatus() {
        viewModelScope.launch {
            val isLoaded = isModelLoadedUseCase()
            Log.d(TAG, "Model loaded status: $isLoaded")
            _uiState.update { it.copy(isModelLoaded = isLoaded) }
        }
    }

    fun refreshModelStatus() {
        checkModelStatus()
    }

    fun selectDirectory(directoryPath: String) {
        _uiState.update {
            it.copy(
                selectedDirectory = directoryPath,
                categorizationState = CategorizationState.Idle,
                scannedFiles = emptyList(),
                categorizationResults = emptyList(),
                moveOperations = emptyList(),
                moveReport = null,
                showMovePreview = false,
                error = null,
            )
        }
        scanDirectory()
    }

    fun scanDirectory() {
        val selectedDirectory = _uiState.value.selectedDirectory
        Log.d(TAG, "scanDirectory called with directory: $selectedDirectory")
        if (selectedDirectory.isNullOrEmpty()) {
            Log.e(TAG, "No directory selected")
            _uiState.update { it.copy(error = "Please select a directory first") }
            return
        }

        scanDirectoryInternal(selectedDirectory, includeSubdirectories = true)
    }

    fun scanAllDirectories() {
        Log.d(TAG, "scanAllDirectories called - this will scan the entire device storage")
        _uiState.update {
            it.copy(
                selectedDirectory = "/storage/emulated/0",
                error = null,
            )
        }
        scanDirectoryInternal("/storage/emulated/0", includeSubdirectories = true)
    }

    private fun scanDirectoryInternal(
        directoryPath: String,
        includeSubdirectories: Boolean,
    ) {
        viewModelScope.launch {
            // Create new session for this scan
            createNewSession(directoryPath)

            _uiState.update {
                it.copy(
                    categorizationState = CategorizationState.ScanningDirectory,
                    error = null,
                )
            }

            try {
                Log.d(TAG, "Starting directory scan for $directoryPath...")
                scanDirectoryUseCase(directoryPath, includeSubdirectories).collect { files ->
                    Log.d(TAG, "Received ${files.size} files from scan")

                    val (initialResults, initialMoveOperations) =
                        withContext(Dispatchers.Default) {
                            val categories = _uiState.value.availableCategories
                            val results =
                                files.take(1000).map { file ->
                                    getQuickCategorizationIfObvious(file, categories)
                                        ?: CategorizationResult(
                                            fileItem = file,
                                            suggestedCategory =
                                                categories.find { it.id == "documents" }
                                                    ?: categories.firstOrNull()
                                                    ?: FileCategory.getDefaultCategories().first(),
                                            confidence = 0.5f,
                                            reasoning = "📁 Extension mapping: ${file.fileType.displayName}",
                                        )
                                }
                            val moveOps = createMoveOperations(results)
                            Pair(results, moveOps)
                        }

                    if (initialResults.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            saveCategorizationResults(initialResults)
                            saveMoveOperations(initialMoveOperations)
                        }
                    }

                    _uiState.update {
                        it.copy(
                            scannedFiles = files,
                            categorizationResults = initialResults,
                            moveOperations = initialMoveOperations,
                            showMovePreview = initialMoveOperations.isNotEmpty(),
                            categorizationState = CategorizationState.Idle,
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during directory scan", e)
                _uiState.update {
                    it.copy(
                        categorizationState = CategorizationState.Failed(e.message ?: "Scan failed"),
                        error = e.message,
                    )
                }
            }
        }
    }

    fun categorizeFiles() {
        val files = _uiState.value.scannedFiles
        val categories = _uiState.value.availableCategories

        Log.d(TAG, "Starting categorization of ${files.size} files")

        if (files.isEmpty()) {
            _uiState.update { it.copy(error = "No files to categorize") }
            return
        }

        if (!_uiState.value.isModelLoaded) {
            _uiState.update { it.copy(error = "Model not loaded. Please load a model first from the side drawer.") }
            return
        }

        viewModelScope.launch {
            val results = mutableListOf<CategorizationResult>()

            _uiState.update {
                it.copy(
                    categorizationState = CategorizationState.CategorizingFiles(0f, "Starting..."),
                    error = null,
                )
            }

            // Process files with smart batching and quick pre-filtering
            files.forEachIndexed { index, file ->
                val progress = index.toFloat() / files.size
                Log.d(TAG, "Categorizing file ${index + 1}/${files.size}: ${file.name}")

                _uiState.update {
                    it.copy(
                        categorizationState = CategorizationState.CategorizingFiles(progress, file.name),
                    )
                }

                try {
                    val currentCategories = _uiState.value.availableCategories
                    var fileResult: CategorizationResult? = null

                    // Invoke Llama LLM to dynamically determine category
                    categorizeFileUseCase(file, currentCategories).collect { result ->
                        fileResult = result
                        Log.d(TAG, "LLM dynamically categorized ${file.name} -> '${result.suggestedCategory.name}' (confidence=${result.confidence})")
                    }

                    // Fallback to quick rule categorization if AI did not return a result
                    if (fileResult == null) {
                        fileResult = getQuickCategorizationIfObvious(file, currentCategories)
                    }

                    fileResult?.let { result ->
                        results.add(result)

                        // Register dynamic category into state
                        val updatedCategories = _uiState.value.availableCategories.toMutableList()
                        if (updatedCategories.none { it.id == result.suggestedCategory.id }) {
                            updatedCategories.add(result.suggestedCategory)
                        }

                        _uiState.update { state ->
                            state.copy(
                                availableCategories = updatedCategories,
                                categorizationResults = results.toList(),
                                moveOperations = createMoveOperations(results.toList()),
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error categorizing ${file.name}", e)
                    val defaultCategory = categories.find { it.id == "documents" } ?: categories.first()
                    results.add(
                        CategorizationResult(
                            fileItem = file,
                            suggestedCategory = defaultCategory,
                            confidence = 0.1f,
                            reasoning = "❌ Error during categorization: ${e.message}",
                        ),
                    )
                }
            }

            Log.d(TAG, "Categorization complete. ${results.size} results generated.")

            // Save categorization results to database
            saveCategorizationResults(results)

            // Create move operations from categorization results
            val moveOperations = createMoveOperations(results)

            // Save move operations to database
            saveMoveOperations(moveOperations)

            _uiState.update {
                it.copy(
                    categorizationState = CategorizationState.CategorizationComplete(results),
                    categorizationResults = results,
                    moveOperations = moveOperations,
                    showMovePreview = true,
                )
            }
        }
    }

    fun confirmCategorization(
        resultIndex: Int,
        confirmed: Boolean,
    ) {
        _uiState.update { state ->
            val updatedResults = state.categorizationResults.toMutableList()
            if (resultIndex in updatedResults.indices) {
                updatedResults[resultIndex] = updatedResults[resultIndex].copy(isConfirmed = confirmed)
            }
            state.copy(categorizationResults = updatedResults)
        }
    }

    fun resetCategorization() {
        _uiState.update {
            it.copy(
                categorizationState = CategorizationState.Idle,
                scannedFiles = emptyList(),
                categorizationResults = emptyList(),
                moveOperations = emptyList(),
                moveReport = null,
                showMovePreview = false,
                error = null,
            )
        }
    }

    fun updateConfidenceThreshold(threshold: Float) {
        _uiState.update { state ->
            val updatedOperations =
                state.moveOperations.map { operation ->
                    operation.copy(isSelected = operation.confidence >= threshold)
                }
            state.copy(moveOperations = updatedOperations)
        }
    }

    private fun createMoveOperations(results: List<CategorizationResult>): List<MoveOperation> {
        val baseDirectory = _uiState.value.selectedDirectory ?: return emptyList()

        return results.mapNotNull { result ->
            val fileParent = result.fileItem.file.parentFile?.name ?: ""
            val categoryName = result.suggestedCategory.name

            // Skip move if file is ALREADY in a folder matching its category (e.g. inside /Books and categorized as Books)
            if (fileParent.equals(categoryName, ignoreCase = true) || fileParent.equals(result.suggestedCategory.id, ignoreCase = true)) {
                Log.d(TAG, "File ${result.fileItem.name} is already in $categoryName folder ($fileParent). Skipping redundant move.")
                return@mapNotNull null
            }

            val categoryFolder = "$baseDirectory/$categoryName"
            val targetPath = "$categoryFolder/${result.fileItem.name}"

            if (targetPath.equals(result.fileItem.path, ignoreCase = true)) {
                return@mapNotNull null
            }

            MoveOperation(
                fileItem = result.fileItem,
                fromPath = result.fileItem.path,
                toPath = targetPath,
                categoryName = categoryName,
                isSelected = result.confidence > 0.7f, // Auto-select high confidence suggestions
            )
        }
    }

    fun toggleMoveOperation(index: Int) {
        _uiState.update { state ->
            val updatedOperations = state.moveOperations.toMutableList()
            if (index in updatedOperations.indices) {
                updatedOperations[index] =
                    updatedOperations[index].copy(
                        isSelected = !updatedOperations[index].isSelected,
                    )
            }
            state.copy(moveOperations = updatedOperations)
        }
    }

    fun selectAllMoveOperations() {
        _uiState.update { state ->
            val updatedOperations = state.moveOperations.map { it.copy(isSelected = true) }
            state.copy(moveOperations = updatedOperations)
        }
    }

    fun deselectAllMoveOperations() {
        _uiState.update { state ->
            val updatedOperations = state.moveOperations.map { it.copy(isSelected = false) }
            state.copy(moveOperations = updatedOperations)
        }
    }

    fun executeMoveOperations() {
        val selectedOperations = _uiState.value.moveOperations.filter { it.isSelected }
        if (selectedOperations.isEmpty()) {
            _uiState.update { it.copy(error = "No operations selected") }
            return
        }

        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val createdDirectories = mutableListOf<String>()
            val errors = mutableListOf<String>()
            var successfulMoves = 0
            var failedMoves = 0

            _uiState.update {
                it.copy(
                    categorizationState = CategorizationState.MovingFiles(0f, "Starting..."),
                    error = null,
                )
            }

            try {
                selectedOperations.forEachIndexed { index, operation ->
                    val progress = index.toFloat() / selectedOperations.size
                    _uiState.update {
                        it.copy(
                            categorizationState =
                                CategorizationState.MovingFiles(
                                    progress,
                                    operation.fileItem.name,
                                ),
                        )
                    }

                    try {
                        // Create directory if it doesn't exist
                        val targetDir = java.io.File(operation.toPath).parentFile
                        if (targetDir != null && !targetDir.exists()) {
                            if (targetDir.mkdirs()) {
                                createdDirectories.add(targetDir.absolutePath)
                                Log.d(TAG, "Created directory: ${targetDir.absolutePath}")
                            }
                        }

                        // Move the file
                        val sourceFile = java.io.File(operation.fromPath)
                        val targetFile = java.io.File(operation.toPath)

                        // Handle file name conflicts
                        val finalTarget =
                            if (targetFile.exists()) {
                                generateUniqueFileName(targetFile)
                            } else {
                                targetFile
                            }

                        if (sourceFile.renameTo(finalTarget)) {
                            successfulMoves++
                            Log.d(TAG, "Moved ${sourceFile.name} to ${finalTarget.absolutePath}")
                        } else {
                            failedMoves++
                            errors.add("Failed to move ${sourceFile.name}")
                            Log.e(TAG, "Failed to move ${sourceFile.name}")
                        }
                    } catch (e: Exception) {
                        failedMoves++
                        errors.add("Error moving ${operation.fileItem.name}: ${e.message}")
                        Log.e(TAG, "Error moving file", e)
                    }
                }

                val duration = System.currentTimeMillis() - startTime
                val report =
                    MoveReport(
                        totalOperations = selectedOperations.size,
                        successfulMoves = successfulMoves,
                        failedMoves = failedMoves,
                        skippedMoves = 0,
                        createdDirectories = createdDirectories.distinct(),
                        errors = errors,
                        duration = duration,
                    )

                _uiState.update {
                    it.copy(
                        categorizationState = CategorizationState.FilesMovedSuccessfully,
                        moveReport = report,
                        showMovePreview = false,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during move operations", e)
                _uiState.update {
                    it.copy(
                        categorizationState = CategorizationState.Failed(e.message ?: "Move failed"),
                        error = e.message,
                    )
                }
            }
        }
    }

    private fun generateUniqueFileName(file: java.io.File): java.io.File {
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
            val newFile = java.io.File(file.parent, newName)
            if (!newFile.exists()) {
                return newFile
            }
            counter++
        }
    }

    private fun getQuickCategorizationIfObvious(
        file: FileItem,
        categories: List<FileCategory>,
    ): CategorizationResult? {
        val fileName = file.name.lowercase()
        val extension = file.extension.lowercase()
        val isInDownloads = file.path.contains("/Download", ignoreCase = true)

        // TIER 1: File type-based categorization (fastest)
        val fileTypeResult = categorizeByFileType(file, extension, categories, isInDownloads)
        if (fileTypeResult != null) return fileTypeResult

        // TIER 2: Filename pattern-based categorization
        val patternResult = categorizeByFilenamePatterns(file, fileName, categories)
        if (patternResult != null) return patternResult

        // TIER 3: Use AI for complex cases
        return null
    }

    private fun categorizeByFileType(
        file: FileItem,
        extension: String,
        categories: List<FileCategory>,
        isInDownloads: Boolean,
    ): CategorizationResult? {
        val filePathLower = file.path.lowercase()
        val fileNameLower = file.name.lowercase()

        // Tier 0: Check if file is in Books directory or is an E-Book format
        if (filePathLower.contains("/books") || extension in listOf("epub", "mobi", "azw", "azw3", "fb2", "djvu") ||
            (extension == "pdf" && (filePathLower.contains("/books") || fileNameLower.contains("book") || fileNameLower.contains("novel") || fileNameLower.contains("guide") || fileNameLower.contains("manual") || fileNameLower.contains("edition")))) {
            categories.find { it.id == "books" }?.let {
                return CategorizationResult(file, it, 0.95f, "📚 Category: Books & Reading Material")
            }
        }

        return when (extension) {
            // Documents
            "pdf", "doc", "docx", "odt", "rtf" -> {
                categories.find { it.id == "documents" }?.let {
                    CategorizationResult(file, it, 0.90f, "📄 Document file: ${extension.uppercase()}")
                }
            }

            "xls", "xlsx", "ods", "csv" -> {
                categories.find { it.id == "documents" }?.let {
                    CategorizationResult(file, it, 0.90f, "📊 Spreadsheet file: ${extension.uppercase()}")
                }
            }

            "ppt", "pptx", "odp" -> {
                categories.find { it.id == "documents" }?.let {
                    CategorizationResult(file, it, 0.90f, "📊 Presentation file: ${extension.uppercase()}")
                }
            }

            // Images
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> {
                categories.find { it.id == "media" || it.id == "personal" }?.let {
                    CategorizationResult(file, it, 0.90f, "🖼️ Image file: ${extension.uppercase()}")
                }
            }

            // Videos
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm" -> {
                categories.find { it.id == "media" }?.let {
                    CategorizationResult(file, it, 0.90f, "🎬 Video file: ${extension.uppercase()}")
                }
            }

            // Audio
            "mp3", "wav", "flac", "aac", "ogg", "m4a" -> {
                categories.find { it.id == "media" }?.let {
                    CategorizationResult(file, it, 0.90f, "🎵 Audio file: ${extension.uppercase()}")
                }
            }

            // Archives
            "zip", "rar", "7z", "tar", "gz", "bz2" -> {
                categories.find { it.id == "downloads" }?.let {
                    CategorizationResult(file, it, 0.85f, "📦 Archive file: ${extension.uppercase()}")
                }
            }

            // Executables/Installers
            "exe", "msi", "dmg", "pkg", "deb", "rpm", "apk" -> {
                categories.find { it.id == "downloads" }?.let {
                    CategorizationResult(file, it, 0.90f, "⚙️ Installer: ${extension.uppercase()}")
                }
            }

            // Text files
            "txt", "md", "log" -> {
                categories.find { it.id == "documents" }?.let {
                    CategorizationResult(file, it, 0.85f, "📝 Text file: ${extension.uppercase()}")
                }
            }

            else -> null
        }
    }

    private fun categorizeByFilenamePatterns(
        file: FileItem,
        fileName: String,
        categories: List<FileCategory>,
    ): CategorizationResult? =
        when {
            // Books & Reading
            fileName.contains("book") || fileName.contains("novel") || fileName.contains("guide") ||
                fileName.contains("manual") || fileName.contains("edition") || fileName.contains("volume") -> {
                categories.find { it.id == "books" }?.let {
                    CategorizationResult(file, it, 0.90f, "📚 Pattern: Book / Novel / Manual / Guide")
                }
            }

            // Financial documents
            fileName.contains("receipt") || fileName.contains("invoice") || fileName.contains("bill") -> {
                categories.find { it.id == "receipts" }?.let {
                    CategorizationResult(file, it, 0.95f, "💰 Pattern: Financial document")
                }
            }

            // Work documents
            fileName.contains("meeting") || fileName.contains("work") || fileName.contains("project") ||
                fileName.contains("report") || fileName.contains("presentation") -> {
                categories.find { it.id == "work" }?.let {
                    CategorizationResult(file, it, 0.9f, "💼 Pattern: Work document")
                }
            }

            // ID Documents
            fileName.contains("passport") || fileName.contains("license") || fileName.contains("id") ||
                fileName.contains("certificate") || fileName.contains("driver") -> {
                categories.find { it.id == "id_docs" }?.let {
                    CategorizationResult(file, it, 0.95f, "🆔 Pattern: ID document")
                }
            }

            // Personal documents
            fileName.contains("vacation") || fileName.contains("personal") || fileName.contains("family") -> {
                categories.find { it.id == "personal" }?.let {
                    CategorizationResult(file, it, 0.85f, "👤 Pattern: Personal document")
                }
            }

            else -> null
        }

    // Session Management Methods
    private suspend fun createNewSession(directoryPath: String) {
        currentSessionId = stateRepository.createSession(directoryPath)
        Log.d(TAG, "Created new session: $currentSessionId")
    }

    private suspend fun saveCategorizationResults(results: List<CategorizationResult>) {
        currentSessionId?.let { sessionId ->
            try {
                stateRepository.saveCategorizationResults(sessionId, results)
                stateRepository.updateSessionStats(
                    sessionId = sessionId,
                    scanned = _uiState.value.scannedFiles.size,
                    categorized = results.size,
                    moved = 0,
                    failed = 0,
                )
                Log.d(TAG, "Saved ${results.size} categorization results to session $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving categorization results", e)
            }
        }
    }

    private suspend fun saveMoveOperations(operations: List<MoveOperation>) {
        currentSessionId?.let { sessionId ->
            try {
                stateRepository.saveMoveOperations(sessionId, operations)
                Log.d(TAG, "Saved ${operations.size} move operations to session $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving move operations", e)
            }
        }
    }
}

data class FileCategorizeUiState(
    val selectedDirectory: String? = null,
    val availableCategories: List<FileCategory> = emptyList(),
    val scannedFiles: List<FileItem> = emptyList(),
    val categorizationResults: List<CategorizationResult> = emptyList(),
    val moveOperations: List<MoveOperation> = emptyList(),
    val moveReport: MoveReport? = null,
    val categorizationState: CategorizationState = CategorizationState.Idle,
    val isModelLoaded: Boolean = false,
    val error: String? = null,
    val showMovePreview: Boolean = false,
)
