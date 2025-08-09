package com.metao.ai.domain.usecase

import com.metao.ai.domain.repository.ModelRepository

class IsModelLoadedUseCase(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(): Boolean {
        return repository.isModelLoaded()
    }
}
