package com.metao.ai.presentation.models

import com.metao.ai.domain.model.DownloadState
import com.metao.ai.domain.model.ModelInfo
import com.metao.ai.domain.model.ModelLoadState

data class ModelsUiState(
    val models: List<ModelInfo> = emptyList(),
    val downloadStates: Map<String, DownloadState> = emptyMap(),
    val loadStates: Map<String, ModelLoadState> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val loadedModelId: String? = null
)
