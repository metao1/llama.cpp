package com.metao.ai.domain.usecase

import com.metao.ai.domain.model.ModelInfo
import com.metao.ai.domain.repository.ModelRepository

class GetModelsUseCase(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(): List<ModelInfo> {
        return repository.getAvailableModels()
    }
}
