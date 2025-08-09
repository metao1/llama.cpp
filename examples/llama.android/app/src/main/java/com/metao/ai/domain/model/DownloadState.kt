package com.metao.ai.domain.model

sealed class DownloadState {
    object Idle : DownloadState()
    object Preparing : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Completed : DownloadState()
    data class Failed(val error: String) : DownloadState()
}

sealed class ModelLoadState {
    object Idle : ModelLoadState()
    object Loading : ModelLoadState()
    object Loaded : ModelLoadState()
    data class Failed(val error: String) : ModelLoadState()
}

sealed class TextGenerationState {
    object Idle : TextGenerationState()
    object Generating : TextGenerationState()
    data class TokenGenerated(val token: String) : TextGenerationState()
    object Completed : TextGenerationState()
    data class Failed(val error: String) : TextGenerationState()
    data class Loading(val error: String) : TextGenerationState()
}
