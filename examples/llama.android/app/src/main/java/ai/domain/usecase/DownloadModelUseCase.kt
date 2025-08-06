package com.metao.ai.domain.usecase

import com.metao.ai.domain.model.DownloadState
import com.metao.ai.domain.model.ModelInfo
import com.metao.ai.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

class DownloadModelUseCase(
    private val repository: ModelRepository
) {
    suspend operator fun invoke(modelInfo: ModelInfo): Flow<DownloadState> {
        return repository.downloadModel(modelInfo)
            .catch { throwable ->
                emit(DownloadState.Failed(throwable.message ?: "Unknown download error"))
            }
    }
}
