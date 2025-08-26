package com.metao.ai.domain.util

object MessageFormatter {
    /**
     * Extracts clean response from model output by removing format tokens
     * Handles both complete and partial tokens during streaming
     */
    fun extractResponse(modelOutput: String): String = modelOutput.replace(Regex("<\\w*[^>]*>?"), "")
}
