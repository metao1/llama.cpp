package com.metao.ai.domain.usecase

import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class MoveFilesUseCase(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(
        results: List<CategorizationResult>,
        baseDirectory: String
    ): Flow<MoveFileProgress> {
        return fileRepository.moveFiles(results, baseDirectory)
            .catch { throwable ->
                emit(MoveFileProgress.Failed(throwable.message ?: "Unknown error during file move"))
            }
    }
}

sealed class MoveFileProgress {
    data class Moving(val progress: Float, val currentFile: String) : MoveFileProgress()
    data class FileMovedSuccessfully(val fileName: String, val newPath: String) : MoveFileProgress()
    data class FileMoveSkipped(val fileName: String, val reason: String) : MoveFileProgress()
    data class FileMoveError(val fileName: String, val error: String) : MoveFileProgress()
    object AllFilesProcessed : MoveFileProgress()
    data class Failed(val error: String) : MoveFileProgress()
}
