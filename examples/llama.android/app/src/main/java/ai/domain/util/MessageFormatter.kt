package com.metao.ai.domain.util

object MessageFormatter {
    /**
     * Builds a complete conversation context with proper formatting
     */
    fun buildConversationPrompt(
        currentMessage: String
    ): String {
        return "<start_of_turn>user\n$currentMessage<end_of_turn>"
    }

    /**
     * Extracts clean response from model output by removing format tokens
     * Handles both complete and partial tokens during streaming
     */
    fun extractResponse(modelOutput: String): String {
        return modelOutput.replace(Regex("<\\w*[^>]*>?"), "")
    }
}
