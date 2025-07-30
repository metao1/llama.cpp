package com.metao.ai.domain.util

object MessageFormatter {

    /**
     * Wraps a message in the proper chat format that the model expects
     * This is crucial for the model to recognize the conversation structure
     */
    fun wrapMessage(message: String): String {
        return "<start_of_turn>user\n$message<end_of_turn>\n<start_of_turn>model\n"
    }

    /**
     * Builds a complete conversation context with proper formatting
     */
    fun buildConversationPrompt(
        currentMessage: String,
        conversationHistory: List<Pair<String, Boolean>> = emptyList()
    ): String {
        val prompt = StringBuilder()

        // Add conversation history
        conversationHistory.forEach { (content, isFromUser) ->
            if (isFromUser) {
                prompt.append("<start_of_turn>user\n$content<end_of_turn>\n")
            } else {
                prompt.append("<start_of_turn>model\n$content<end_of_turn>\n")
            }
        }

        // Add current message
        prompt.append("<start_of_turn>user\n$currentMessage<end_of_turn>\n")
        prompt.append("<start_of_turn>model\n")

        return prompt.toString()
    }

    /**
     * Extracts clean response from model output by removing format tokens
     * Handles both complete and partial tokens during streaming
     */
    fun extractResponse(modelOutput: String): String {
        return modelOutput.replace(Regex("<\\w*[^>]*>?"), "")
    }
}
