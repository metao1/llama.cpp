package com.metao.ai.presentation.models

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import com.metao.ai.domain.manager.ModelStateManager
import com.metao.ai.domain.model.DownloadState
import com.metao.ai.domain.model.ModelInfo
import com.metao.ai.domain.model.ModelLoadState
import com.metao.ai.domain.usecase.AddCustomModelUseCase
import com.metao.ai.domain.usecase.DownloadModelUseCase
import com.metao.ai.domain.usecase.GetModelsUseCase
import com.metao.ai.domain.usecase.LoadModelUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ModelsViewModel(
    private val getModelsUseCase: GetModelsUseCase,
    private val addCustomModelUseCase: AddCustomModelUseCase,
    private val downloadModelUseCase: DownloadModelUseCase,
    private val loadModelUseCase: LoadModelUseCase,
    private val modelStateManager: ModelStateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelsUiState())
    val uiState: StateFlow<ModelsUiState> = _uiState.asStateFlow()

    // Don't load models automatically in init to avoid hanging
    // Models will be loaded when the screen is first displayed

    fun loadModels() {
        viewModelScope.launch {
            Log.d("ModelsViewModel", "Loading models...")
            _uiState.update { it.copy(isLoading = true) }
            try {
                val models = withContext(Dispatchers.IO) {
                    getModelsUseCase()
                }
                Log.d("ModelsViewModel", "Loaded ${models.size} models from use case")
                _uiState.update {
                    it.copy(
                        models = models,
                        isLoading = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                Log.e("ModelsViewModel", "Failed to load models", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load models"
                    )
                }
            }
        }
    }

    fun downloadModel(modelInfo: ModelInfo) {
        viewModelScope.launch {
            downloadModelUseCase(modelInfo).collect { state ->
                _uiState.update { currentState ->
                    currentState.copy(
                        downloadStates = currentState.downloadStates + (modelInfo.id to state)
                    )
                }

                if (state is DownloadState.Completed) {
                    // Refresh models list to update download status
                    loadModels()
                }
            }
        }
    }

    fun loadModel(modelInfo: ModelInfo) {
        if (!modelInfo.isDownloaded) return

        viewModelScope.launch {
            loadModelUseCase(modelInfo.destinationFile.absolutePath).collect { state ->
                _uiState.update { currentState ->
                    currentState.copy(
                        loadStates = currentState.loadStates + (modelInfo.id to state)
                    )
                }

                when (state) {
                    is ModelLoadState.Loaded -> {
                        _uiState.update {
                            it.copy(loadedModelId = modelInfo.id)
                        }
                        // Notify the shared state manager
                        modelStateManager.setModelLoaded(modelInfo.id)
                    }
                    is ModelLoadState.Failed -> {
                        _uiState.update {
                            it.copy(error = state.error)
                        }
                        // Clear the loaded state on failure
                        modelStateManager.setModelUnloaded()
                    }
                    else -> { /* Do nothing */ }
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun getDownloadState(modelId: String): DownloadState {
        return _uiState.value.downloadStates[modelId] ?: DownloadState.Idle
    }

    fun getLoadState(modelId: String): ModelLoadState {
        return _uiState.value.loadStates[modelId] ?: ModelLoadState.Idle
    }

    fun isModelLoaded(modelId: String): Boolean {
        return _uiState.value.loadedModelId == modelId
    }

    fun addCustomModel(modelData: AddModelDialogData) {
        viewModelScope.launch {
            try {
                // Create a custom model info
                val customModel = ModelInfo(
                    id = "custom_${System.currentTimeMillis()}",
                    name = modelData.name,
                    description = modelData.description,
                    sourceUrl = Uri.parse(modelData.url),
                    destinationFile = File("/storage/emulated/0/Android/data/com.metao.ai/files/models/${modelData.name.replace(" ", "_").lowercase()}.gguf"),
                    sizeBytes = modelData.sizeBytes,
                    isDownloaded = false
                )

                // Save to database (this will persist the model)
                addCustomModelUseCase(customModel)

                // Reload models from database to update UI
                loadModels()

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to add model: ${e.message}")
                }
            }
        }
    }
}

data class AddModelDialogData(
    val name: String,
    val description: String,
    val url: String,
    val sizeBytes: Long
)
