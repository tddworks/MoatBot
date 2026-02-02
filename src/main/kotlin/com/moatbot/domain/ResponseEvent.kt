package com.moatbot.domain

/**
 * Events emitted during a chat response stream.
 * Used to communicate progress from Moatbot to gateways.
 */
sealed class ResponseEvent {
    /**
     * A chunk of text from the AI response.
     */
    data class TextChunk(val text: String) : ResponseEvent()

    /**
     * A tool call has been started.
     */
    data class ToolStarted(val call: ToolCall) : ResponseEvent()

    /**
     * A tool call has completed with a result.
     */
    data class ToolCompleted(val callId: ToolCallId, val result: String) : ResponseEvent()

    /**
     * The response has completed successfully.
     */
    data class Completed(val response: String) : ResponseEvent()

    /**
     * The response has failed with an error.
     */
    data class Failed(val error: String) : ResponseEvent()
}
