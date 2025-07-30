package com.metao.ai.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metao.ai.domain.manager.ModelStateManager
import com.metao.ai.domain.model.ChatMessage
import com.metao.ai.domain.model.TextGenerationState
import com.metao.ai.domain.usecase.ClearMessagesUseCase
import com.metao.ai.domain.usecase.GenerateTextUseCase
import com.metao.ai.domain.usecase.IsModelLoadedUseCase
import com.metao.ai.domain.util.MessageFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val generateTextUseCase: GenerateTextUseCase,
    private val isModelLoadedUseCase: IsModelLoadedUseCase,
    private val clearMessagesUseCase: ClearMessagesUseCase,
    private val modelStateManager: ModelStateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Observe model state changes
        viewModelScope.launch {
            modelStateManager.isModelLoaded.collect { isLoaded ->
                _uiState.update { it.copy(isModelLoaded = isLoaded) }
            }
        }

        // Initial check
        checkModelStatus()
    }

    fun updateInput(input: String) {
        _uiState.update { it.copy(currentInput = input) }
    }

    fun sendMessage() {
        val currentInput = _uiState.value.currentInput.trim()
        if (currentInput.isEmpty() || _uiState.value.isGenerating) return

        // Add user message
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = currentInput,
            isFromUser = true
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                currentInput = "",
                isGenerating = true,
                error = null
            )
        }

        // Add empty assistant message that will be filled with tokens
        val assistantMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            content = "",
            isFromUser = false
        )

        _uiState.update {
            it.copy(messages = it.messages + assistantMessage)
        }

        // Generate response with proper conversation context
        viewModelScope.launch {
            try {
                // Create properly formatted prompt with start_of_turn tokens
                val formattedPrompt = MessageFormatter.buildConversationPrompt(currentInput)

                generateTextUseCase(formattedPrompt, useChat = false).collect { state ->
                    when (state) {
                        is TextGenerationState.Generating -> {
                            _uiState.update { it.copy(isGenerating = true) }
                        }
                        is TextGenerationState.TokenGenerated -> {
                            _uiState.update { currentState ->
                                val updatedMessages = currentState.messages.toMutableList()
                                val lastMessageIndex = updatedMessages.lastIndex
                                if (lastMessageIndex >= 0 && !updatedMessages[lastMessageIndex].isFromUser) {
                                    updatedMessages[lastMessageIndex] = updatedMessages[lastMessageIndex].copy(
                                        content = updatedMessages[lastMessageIndex].content + state.token
                                    )
                                }
                                currentState.copy(messages = updatedMessages)
                            }
                        }
                        is TextGenerationState.Completed -> {
                            _uiState.update { it.copy(isGenerating = false) }
                        }
                        is TextGenerationState.Failed -> {
                            _uiState.update {
                                it.copy(
                                    isGenerating = false,
                                    error = state.error
                                )
                            }
                        }
                        is TextGenerationState.Idle -> {
                            // Do nothing
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        error = "Unexpected error: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            try {
                // Clear messages in the repository (LLamaAndroid)
                clearMessagesUseCase()

                // Clear messages in the UI
                _uiState.update {
                    it.copy(
                        messages = emptyList(),
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to clear messages: ${e.message}")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun setModelLoaded(isLoaded: Boolean) {
        _uiState.update { it.copy(isModelLoaded = isLoaded) }
    }

    fun checkModelStatus() {
        viewModelScope.launch {
            try {
                val isLoaded = isModelLoadedUseCase()
                _uiState.update { it.copy(isModelLoaded = isLoaded) }
            } catch (e: Exception) {
                // If we can't check, assume no model is loaded
                _uiState.update { it.copy(isModelLoaded = false) }
            }
        }
    }
}
