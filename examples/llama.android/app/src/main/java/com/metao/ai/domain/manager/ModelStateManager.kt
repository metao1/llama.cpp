package com.metao.ai.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModelStateManager {
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _loadedModelId = MutableStateFlow<String?>(null)
    val loadedModelId: StateFlow<String?> = _loadedModelId.asStateFlow()

    fun setModelLoaded(modelId: String) {
        _loadedModelId.value = modelId
        _isModelLoaded.value = true
    }

    fun setModelUnloaded() {
        _loadedModelId.value = null
        _isModelLoaded.value = false
    }
}
