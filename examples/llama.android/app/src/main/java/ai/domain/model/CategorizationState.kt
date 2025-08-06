package com.metao.ai.domain.model

sealed class CategorizationState {
    object Idle : CategorizationState()
    object ScanningDirectory : CategorizationState()
    data class CategorizingFiles(val progress: Float, val currentFile: String) : CategorizationState()
    data class CategorizationComplete(val results: List<CategorizationResult>) : CategorizationState()
    data class MovingFiles(val progress: Float, val currentFile: String) : CategorizationState()
    object FilesMovedSuccessfully : CategorizationState()
    data class Failed(val error: String) : CategorizationState()
}

data class CategorizationResult(
    val fileItem: FileItem,
    val suggestedCategory: FileCategory,
    val confidence: Float,
    val reasoning: String = "",
    val isConfirmed: Boolean = false
)

data class CategorizationBatch(
    val sourceDirectory: String,
    val results: List<CategorizationResult>,
    val totalFiles: Int,
    val processedFiles: Int,
    val skippedFiles: Int = 0,
    val errorFiles: Int = 0
)

data class MoveOperation(
    val fileItem: FileItem,
    val fromPath: String,
    val toPath: String,
    val categoryName: String,
    val isSelected: Boolean = true,
    val confidence: Float = 0.0f,
    val reasoning: String = ""
) {
    val sourceFilePath: String get() = fromPath
    val targetDirectoryPath: String get() = toPath.substringBeforeLast("/")
    val fileName: String get() = fileItem.name
}

data class MoveReport(
    val totalOperations: Int,
    val successfulMoves: Int,
    val failedMoves: Int,
    val skippedMoves: Int,
    val createdDirectories: List<String>,
    val errors: List<String>,
    val duration: Long
)
