package com.metao.ai.presentation.categorize

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metao.ai.domain.manager.ModelStateManager
import com.metao.ai.domain.model.FileCategory
import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.model.CategorizationState
import com.metao.ai.domain.model.MoveOperation
import com.metao.ai.domain.model.MoveReport
import com.metao.ai.data.repository.CategorizationStateRepository
import com.metao.ai.domain.usecase.ScanDirectoryUseCase
import com.metao.ai.domain.usecase.CategorizeFileUseCase
import com.metao.ai.domain.usecase.IsModelLoadedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FileCategorizeViewModel(
    private val scanDirectoryUseCase: ScanDirectoryUseCase,
    private val categorizeFileUseCase: CategorizeFileUseCase,
    private val isModelLoadedUseCase: IsModelLoadedUseCase,
    private val modelStateManager: ModelStateManager,
    private val stateRepository: CategorizationStateRepository
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

        // Try to restore last session
        viewModelScope.launch {
            restoreLastSession()
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
                categorizationResults = emptyList()
            )
        }
    }

    fun scanDirectory() {
        val selectedDirectory = _uiState.value.selectedDirectory
        Log.d(TAG, "scanDirectory called with directory: $selectedDirectory")
        if (selectedDirectory.isNullOrEmpty()) {
            Log.e(TAG, "No directory selected")
            _uiState.update { it.copy(error = "Please select a directory first") }
            return
        }

        scanDirectoryInternal(selectedDirectory, includeSubdirectories = false)
    }

    fun scanAllDirectories() {
        Log.d(TAG, "scanAllDirectories called - this will scan the entire device storage")
        _uiState.update {
            it.copy(
                selectedDirectory = "/storage/emulated/0",
                error = null
            )
        }
        scanDirectoryInternal("/storage/emulated/0", includeSubdirectories = true)
    }

    private fun scanDirectoryInternal(directoryPath: String, includeSubdirectories: Boolean) {
        viewModelScope.launch {
            // Create new session for this scan
            createNewSession(directoryPath)

            _uiState.update {
                it.copy(
                    categorizationState = CategorizationState.ScanningDirectory,
                    error = null
                )
            }

            try {
                Log.d(TAG, "Starting directory scan...")
                scanDirectoryUseCase(directoryPath, includeSubdirectories).collect { files ->
                    Log.d(TAG, "Received ${files.size} files from scan")
                    _uiState.update {
                        it.copy(
                            scannedFiles = files,
                            categorizationState = CategorizationState.Idle
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during directory scan", e)
                _uiState.update {
                    it.copy(
                        categorizationState = CategorizationState.Failed(e.message ?: "Scan failed"),
                        error = e.message
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
                    error = null
                )
            }

            // Process files with smart batching and quick pre-filtering
            files.forEachIndexed { index, file ->
                val progress = index.toFloat() / files.size
                Log.d(TAG, "Categorizing file ${index + 1}/${files.size}: ${file.name}")

                _uiState.update {
                    it.copy(
                        categorizationState = CategorizationState.CategorizingFiles(progress, file.name)
                    )
                }

                try {
                    // Quick rule-based pre-check for obvious cases
                    val quickResult = getQuickCategorizationIfObvious(file, categories)

                    val fileResult = if (quickResult != null) {
                        Log.d(TAG, "Quick categorized ${file.name} -> ${quickResult.suggestedCategory.name}")
                        quickResult
                    } else {
                        // Use AI for complex cases
                        var aiResult: CategorizationResult? = null
                        categorizeFileUseCase(file, categories).collect { result ->
                            aiResult = result
                            Log.d(TAG, "AI categorized ${file.name} -> ${result.suggestedCategory.name} (${result.confidence})")
                        }
                        aiResult
                    }

                    fileResult?.let {
                        results.add(it)

                        // Emit intermediate results every 3 files for better UX
                        if (results.size % 3 == 0) {
                            _uiState.update {
                                it.copy(
                                    categorizationResults = results.toList(),
                                    moveOperations = createMoveOperations(results.toList())
                                )
                            }
                        }
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error categorizing ${file.name}", e)
                    // Add a default result for failed categorization
                    val defaultCategory = categories.find { it.id == "downloads" } ?: categories.first()
                    results.add(
                        CategorizationResult(
                            fileItem = file,
                            suggestedCategory = defaultCategory,
                            confidence = 0.1f,
                            reasoning = "❌ Error during categorization: ${e.message}"
                        )
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
                    showMovePreview = true
                )
            }
        }
    }

    fun confirmCategorization(resultIndex: Int, confirmed: Boolean) {
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
                error = null
            )
        }
    }

    private fun createMoveOperations(results: List<CategorizationResult>): List<MoveOperation> {
        val baseDirectory = _uiState.value.selectedDirectory ?: return emptyList()

        return results.map { result ->
            val categoryFolder = "${baseDirectory}/${result.suggestedCategory.name}"
            MoveOperation(
                fileItem = result.fileItem,
                fromPath = result.fileItem.path,
                toPath = "${categoryFolder}/${result.fileItem.name}",
                categoryName = result.suggestedCategory.name,
                isSelected = result.confidence > 0.7f // Auto-select high confidence suggestions
            )
        }
    }

    fun toggleMoveOperation(index: Int) {
        _uiState.update { state ->
            val updatedOperations = state.moveOperations.toMutableList()
            if (index in updatedOperations.indices) {
                updatedOperations[index] = updatedOperations[index].copy(
                    isSelected = !updatedOperations[index].isSelected
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
                    error = null
                )
            }

            try {
                selectedOperations.forEachIndexed { index, operation ->
                    val progress = index.toFloat() / selectedOperations.size
                    _uiState.update {
                        it.copy(
                            categorizationState = CategorizationState.MovingFiles(
                                progress,
                                operation.fileItem.name
                            )
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
                        val finalTarget = if (targetFile.exists()) {
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
                val report = MoveReport(
                    totalOperations = selectedOperations.size,
                    successfulMoves = successfulMoves,
                    failedMoves = failedMoves,
                    skippedMoves = 0,
                    createdDirectories = createdDirectories.distinct(),
                    errors = errors,
                    duration = duration
                )

                _uiState.update {
                    it.copy(
                        categorizationState = CategorizationState.FilesMovedSuccessfully,
                        moveReport = report,
                        showMovePreview = false
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error during move operations", e)
                _uiState.update {
                    it.copy(
                        categorizationState = CategorizationState.Failed(e.message ?: "Move failed"),
                        error = e.message
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
            val newName = if (extension.isNotEmpty()) {
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
        categories: List<FileCategory>
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
        isInDownloads: Boolean
    ): CategorizationResult? {
        return when (extension) {
            // Documents
            "pdf" -> {
                val targetFolder = if (isInDownloads) "Documents/PDF" else "Documents"
                categories.find { it.id == "documents" }?.let {
                    CategorizationResult(file, it, 0.95f, "📄 File type: PDF → $targetFolder")
                }
            }

            "doc", "docx", "odt", "rtf" -> {
                val targetFolder = if (isInDownloads) "Documents/Word" else "Documents"
                categories.find { it.id == "documents" }?.let {
                    CategorizationResult(file, it, 0.95f, "📄 File type: Word → $targetFolder")
                }
            }

            "xls", "xlsx", "ods", "csv" -> {
                val targetFolder = if (isInDownloads) "Documents/Spreadsheets" else "Documents"
                categories.find { it.id == "documents" }?.let {
                    CategorizationResult(file, it, 0.95f, "📊 File type: Spreadsheet → $targetFolder")
                }
            }

            "ppt", "pptx", "odp" -> {
                val targetFolder = if (isInDownloads) "Documents/Presentations" else "Documents"
                categories.find { it.id == "documents" }?.let {
                    CategorizationResult(file, it, 0.95f, "📊 File type: Presentation → $targetFolder")
                }
            }

            // Images
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "svg" -> {
                val targetFolder = if (isInDownloads) "Pictures/Downloads" else "Pictures"
                categories.find { it.id == "media" }?.let {
                    CategorizationResult(file, it, 0.9f, "🖼️ File type: Image → $targetFolder")
                }
            }

            // Videos
            "mp4", "avi", "mkv", "mov", "wmv", "flv", "webm" -> {
                val targetFolder = if (isInDownloads) "Movies/Downloads" else "Movies"
                categories.find { it.id == "media" }?.let {
                    CategorizationResult(file, it, 0.9f, "🎬 File type: Video → $targetFolder")
                }
            }

            // Audio
            "mp3", "wav", "flac", "aac", "ogg", "m4a" -> {
                val targetFolder = if (isInDownloads) "Music/Downloads" else "Music"
                categories.find { it.id == "media" }?.let {
                    CategorizationResult(file, it, 0.9f, "🎵 File type: Audio → $targetFolder")
                }
            }

            // Archives
            "zip", "rar", "7z", "tar", "gz", "bz2" -> {
                val targetFolder = if (isInDownloads) "Downloads/Archives" else "Downloads"
                categories.find { it.id == "downloads" }?.let {
                    CategorizationResult(file, it, 0.85f, "📦 File type: Archive → $targetFolder")
                }
            }

            // Executables/Installers
            "exe", "msi", "dmg", "pkg", "deb", "rpm", "apk" -> {
                val targetFolder = if (isInDownloads) "Downloads/Software" else "Downloads"
                categories.find { it.id == "downloads" }?.let {
                    CategorizationResult(file, it, 0.9f, "⚙️ File type: Installer → $targetFolder")
                }
            }

            // Text files
            "txt", "md", "log" -> {
                val targetFolder = if (isInDownloads) "Documents/Text" else "Documents"
                categories.find { it.id == "documents" }?.let {
                    CategorizationResult(file, it, 0.8f, "📝 File type: Text → $targetFolder")
                }
            }

            else -> null
        }
    }

    private fun categorizeByFilenamePatterns(
        file: FileItem,
        fileName: String,
        categories: List<FileCategory>
    ): CategorizationResult? {
        return when {
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
    }

    // Session Management Methods
    private suspend fun restoreLastSession() {
        try {
            val lastSession = stateRepository.getLastSession()
            if (lastSession != null && !lastSession.isCompleted) {
                Log.d(TAG, "Restoring last session: ${lastSession.sessionId}")
                currentSessionId = lastSession.sessionId

                // Restore directory selection
                _uiState.update { it.copy(selectedDirectory = lastSession.directoryPath) }

                // Restore categorization results
                stateRepository.getCategorizationResults(lastSession.sessionId).collect { results ->
                    if (results.isNotEmpty()) {
                        Log.d(TAG, "Restored ${results.size} categorization results")
                        _uiState.update {
                            it.copy(
                                categorizationResults = results,
                                categorizationState = CategorizationState.CategorizationComplete(results)
                            )
                        }

                        // Restore move operations
                        stateRepository.getMoveOperations(lastSession.sessionId).collect { operations ->
                            if (operations.isNotEmpty()) {
                                Log.d(TAG, "Restored ${operations.size} move operations")
                                _uiState.update {
                                    it.copy(
                                        moveOperations = operations,
                                        showMovePreview = true
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring last session", e)
        }
    }

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
                    failed = 0
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
    val showMovePreview: Boolean = false
)
