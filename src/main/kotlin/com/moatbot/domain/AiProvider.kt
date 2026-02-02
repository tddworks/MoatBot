package com.moatbot.domain

import kotlinx.coroutines.flow.Flow

/**
 * Interface for providing AI completions.
 */
interface AiProvider {
    /**
     * Generate AI completion from messages.
     * Returns a flow of AI events for streaming response.
     */
    fun complete(messages: List<Message>, sessionId: String?): Flow<AiEvent>
}

/**
 * Events from the AI provider during completion.
 */
sealed class AiEvent {
    /**
     * Text content from the AI.
     */
    data class Text(val text: String) : AiEvent()

    /**
     * AI has requested a tool call.
     */
    data class ToolCall(val call: com.moatbot.domain.ToolCall) : AiEvent()

    /**
     * AI session ID for resuming conversations.
     */
    data class SessionId(val id: String) : AiEvent()

    /**
     * AI has finished generating.
     */
    data object Done : AiEvent()

    /**
     * An error occurred during generation.
     */
    data class Error(val message: String) : AiEvent()
}
