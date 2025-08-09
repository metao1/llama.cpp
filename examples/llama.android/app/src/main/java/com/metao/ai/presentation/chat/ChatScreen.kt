package com.metao.ai.presentation.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import kotlin.math.min

@Composable
fun ChatScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val maxWidth = min(configuration.screenWidthDp.dp.value, 600f).dp

    // Check model status when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.checkModelStatus()
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxHeight()
        ) {
            ChatMessages(
                messages = uiState.messages,
                isGenerating = uiState.isGenerating,
                modifier = Modifier.weight(1f)
            )

            ChatInput(
                input = uiState.currentInput,
                onInputChange = viewModel::updateInput,
                onSendMessage = viewModel::sendMessage,
                isGenerating = uiState.isGenerating,
                isModelLoaded = uiState.isModelLoaded
            )
        }
    }

    // Show error if any
    uiState.error?.let { error ->
        LaunchedEffect(error) {
            // Auto-clear error after 5 seconds
            delay(5000)
            viewModel.clearError()
        }
    }
}

@Composable
private fun ChatMessages(
    messages: List<com.metao.ai.domain.model.ChatMessage>,
    isGenerating: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()

    // Auto-scroll to bottom when new messages are added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        state = scrollState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages) { message ->
            MessageBubble(message = message)
        }

        if (isGenerating && messages.isNotEmpty() && !messages.last().isFromUser) {
            item {
                TypingIndicator()
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: com.metao.ai.domain.model.ChatMessage,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (message.isFromUser) Color(0xFFE3F2FD) else Color.White,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = message.content,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black
        )
    }
}

@Composable
private fun ChatInput(
    input: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    isGenerating: Boolean,
    isModelLoaded: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    if (isModelLoaded) "Type a message..."
                    else "Load a model first..."
                )
            },
            enabled = isModelLoaded && !isGenerating,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp, max = 120.dp),
            maxLines = 4
        )

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onSendMessage,
            enabled = isModelLoaded && !isGenerating && input.isNotBlank(),
            modifier = Modifier.height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White,
                disabledContainerColor = Color.Gray,
                disabledContentColor = Color.White
            )
        ) {
            Text("Send")
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Generating",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.width(8.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
    }
}
