package com.metao.ai.domain.usecase

import com.metao.ai.domain.model.TextGenerationState
import com.metao.ai.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class GenerateTextUseCase(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(prompt: String, useChat: Boolean = true): Flow<TextGenerationState> {
        return repository.generateText(prompt, useChat)
            .catch { throwable ->
                emit(TextGenerationState.Failed(throwable.message ?: "Unknown generation error"))
            }
    }
}
