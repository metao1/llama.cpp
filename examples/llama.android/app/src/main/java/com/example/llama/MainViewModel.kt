package com.example.llama

import android.llama.cpp.LLamaAndroid
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class MainViewModel(private val llamaAndroid: LLamaAndroid = LLamaAndroid.instance()): ViewModel() {
    companion object {
        @JvmStatic
        private val NanosPerSecond = 1_000_000_000.0
    }

    private val tag: String? = this::class.simpleName

    var messages by mutableStateOf(listOf(""))
        private set

    private var message by mutableStateOf("")

    override fun onCleared() {
        super.onCleared()

        viewModelScope.launch {
            try {
                llamaAndroid.unload()
            } catch (exc: IllegalStateException) {
                messages += exc.message!!
            }
        }
    }

    fun send() {
        val text = message
        message = ""
        if( text.isBlank()) {
            Log.w(tag, "send() called with empty text")
            return
        }
        // Add to messages console.
        messages += text
        messages += ""

        Log.d(tag, "Sending message: '$text'")

        viewModelScope.launch {
            try {
                var tokenCount = 0
                // Try with chat formatting first, then without if that fails
                val formatChat = true
                Log.d(tag, "Attempting with chat formatting: $formatChat")
                if (text.isEmpty()) {
                    Log.w(tag, "send() called with empty text")
                    return@launch
                }
                llamaAndroid.send(wrapMessage(text), formatChat)
                    .catch {
                        Log.e(tag, "send() failed", it)
                        messages += "Error: ${it.message}"
                    }
                    .collect { token ->
                        tokenCount++
                        Log.d(tag, "Received token $tokenCount: '$token'")
                        if (messages.isEmpty()) {
                            return@collect
                        }
                        messages = messages.dropLast(1) + (messages.last() + token)
                    }
                Log.d(tag, "Text generation completed. Total tokens received: $tokenCount")

                if (tokenCount == 0) {
                    messages += "No response generated. Try a different prompt or check logs."
                }
            } catch (e: Exception) {
                Log.e(tag, "Exception in send()", e)
                messages += "Exception: ${e.message}"
            }
            //messages = unwrapMessage(message)
        }
    }

    private fun unwrapMessage(message: String): List<String> {
        if (message.isBlank()) {
            return listOf()
        }
        return message.split("\\s\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replace("<start_of_turn>model", "").trim() }
            .map { it.replace("<start_of_turn>user", "").trim() }
            .map { it.replace("<end_of_turn>", "").trim() }
            .filter { it.isNotEmpty() }
    }

    private fun wrapMessage(message: String): String {
        if (message.isBlank()) {
            return message
        }
        return """
        <start_of_turn>user
        $message
        <end_of_turn>
        <start_of_turn>model
        """.trimIndent()
    }

    fun bench(pp: Int, tg: Int, pl: Int, nr: Int = 1) {
        viewModelScope.launch {
            try {
                val start = System.nanoTime()
                val warmupResult = llamaAndroid.bench(pp, tg, pl, nr)
                val end = System.nanoTime()

                messages += warmupResult

                val warmup = (end - start).toDouble() / NanosPerSecond
                messages += "Warm up time: $warmup seconds, please wait..."

                if (warmup > 5.0) {
                    messages += "Warm up took too long, aborting benchmark"
                    return@launch
                }

                messages += llamaAndroid.bench(512, 128, 1, 3)
            } catch (exc: IllegalStateException) {
                Log.e(tag, "bench() failed", exc)
                messages += exc.message!!
            }
        }
    }

    fun load(pathToModel: String) {
        viewModelScope.launch {
            try {
                llamaAndroid.load(pathToModel)
                messages += "Loaded $pathToModel"
            } catch (exc: IllegalStateException) {
                Log.e(tag, "load() failed", exc)
                messages += exc.message!!
            }
        }
    }

    fun updateMessage(newMessage: String) {
        message = newMessage
    }

    fun clear() {
        messages = listOf()
    }

    fun log(message: String) {
        messages += message
    }
}
