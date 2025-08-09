package com.metao.ai.domain.usecase

import com.metao.ai.domain.repository.ModelRepository

class ClearMessagesUseCase(
    private val repository: ModelRepository
) {
    suspend operator fun invoke() {
        repository.clearMessages()
    }
}
