package com.metao.ai.domain.usecase

import com.metao.ai.domain.model.ModelLoadState
import com.metao.ai.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class LoadModelUseCase(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(modelPath: String): Flow<ModelLoadState> {
        return repository.loadModel(modelPath)
            .catch { throwable ->
                emit(ModelLoadState.Failed(throwable.message ?: "Unknown load error"))
            }
    }
}
