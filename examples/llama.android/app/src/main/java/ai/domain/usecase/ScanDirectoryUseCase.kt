package com.metao.ai.domain.usecase

import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.io.File

class ScanDirectoryUseCase(
    private val fileRepository: FileRepository
) {
    suspend operator fun invoke(
        directoryPath: String,
        includeSubdirectories: Boolean = false,
        maxFileSizeForContent: Long = 1024 * 1024 // 1MB
    ): Flow<List<FileItem>> {
        return fileRepository.scanDirectory(directoryPath, includeSubdirectories, maxFileSizeForContent)
            .catch { throwable ->
                emit(emptyList())
            }
    }
}
