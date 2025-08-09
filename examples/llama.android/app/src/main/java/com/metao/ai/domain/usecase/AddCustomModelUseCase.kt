package com.metao.ai.domain.usecase

import com.metao.ai.domain.model.ModelInfo
import com.metao.ai.domain.repository.ModelRepository

class AddCustomModelUseCase(
    private val modelRepository: ModelRepository
) {
    suspend operator fun invoke(modelInfo: ModelInfo) {
        modelRepository.addCustomModel(modelInfo)
    }
}
