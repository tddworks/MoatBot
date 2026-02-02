package com.moatbot.domain

import java.time.Instant

/**
 * Represents the current turn state during an AI response.
 * A turn starts when the user sends a message and ends when the AI completes its response.
 */
data class Turn(
    val text: String = "",
    val pendingToolCalls: List<ToolCall> = emptyList(),
    val completedToolCalls: List<CompletedToolCall> = emptyList(),
    val startedAt: Instant = Instant.now()
) {
    fun appendText(chunk: String): Turn = copy(text = text + chunk)

    fun addToolCall(call: ToolCall): Turn = copy(
        pendingToolCalls = pendingToolCalls + call
    )

    fun completeToolCall(callId: ToolCallId, result: String): Turn = copy(
        pendingToolCalls = pendingToolCalls.filter { it.id != callId },
        completedToolCalls = completedToolCalls + CompletedToolCall(callId, result)
    )

    fun hasPendingTools(): Boolean = pendingToolCalls.isNotEmpty()
}

data class CompletedToolCall(
    val callId: ToolCallId,
    val result: String
)
