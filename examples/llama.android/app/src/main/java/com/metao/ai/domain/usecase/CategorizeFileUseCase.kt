package com.metao.ai.domain.usecase

import com.metao.ai.domain.model.FileItem
import com.metao.ai.domain.model.FileCategory
import com.metao.ai.domain.model.CategorizationResult
import com.metao.ai.domain.repository.FileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class CategorizeFileUseCase(
    private val fileRepository: FileRepository,
    private val generateTextUseCase: GenerateTextUseCase
) {
    suspend operator fun invoke(
        fileItem: FileItem,
        availableCategories: List<FileCategory>
    ): Flow<CategorizationResult> {
        return fileRepository.categorizeFile(fileItem, availableCategories, generateTextUseCase)
            .catch { throwable ->
                // Return a default categorization result on error
                val defaultCategory = availableCategories.find { it.id == "downloads" } 
                    ?: availableCategories.firstOrNull()
                    ?: FileCategory.getDefaultCategories().first()
                
                emit(
                    CategorizationResult(
                        fileItem = fileItem,
                        suggestedCategory = defaultCategory,
                        confidence = 0.1f,
                        reasoning = "Error during categorization: ${throwable.message}",
                        isConfirmed = false
                    )
                )
            }
    }
}
