package com.moatbot.domain

import java.time.Instant

/**
 * Aggregate root for a conversation.
 * Manages conversation state and behavior including messages and current turn.
 */
data class Conversation(
    val id: ConversationId,
    val key: SessionKey,
    val messages: List<Message>,
    val turn: Turn?,
    val aiSessionId: String?,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    companion object {
        fun start(key: SessionKey): Conversation {
            val now = Instant.now()
            return Conversation(
                id = ConversationId.generate(),
                key = key,
                messages = emptyList(),
                turn = null,
                aiSessionId = null,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    /**
     * Receive a user message and start a new turn.
     */
    fun receive(userId: UserId, content: String): Conversation {
        val userMessage = Message.User.create(userId, content)
        return copy(
            messages = messages + userMessage,
            turn = Turn(),
            updatedAt = Instant.now()
        )
    }

    /**
     * Add a text chunk to the current turn.
     */
    fun addTextChunk(chunk: String): Conversation {
        val currentTurn = turn ?: Turn()
        return copy(
            turn = currentTurn.appendText(chunk),
            updatedAt = Instant.now()
        )
    }

    /**
     * Add a tool call to the current turn.
     */
    fun addToolCall(call: ToolCall): Conversation {
        val currentTurn = turn ?: Turn()
        return copy(
            turn = currentTurn.addToolCall(call),
            updatedAt = Instant.now()
        )
    }

    /**
     * Add a tool result to the current turn.
     */
    fun addToolResult(callId: ToolCallId, result: String): Conversation {
        val currentTurn = turn ?: return this
        return copy(
            turn = currentTurn.completeToolCall(callId, result),
            updatedAt = Instant.now()
        )
    }

    /**
     * Complete the current turn with the final response text.
     */
    fun complete(finalText: String): Conversation {
        val assistantMessage = Message.Assistant.create(finalText)
        return copy(
            messages = messages + assistantMessage,
            turn = null,
            updatedAt = Instant.now()
        )
    }

    /**
     * Fail the current turn with an error.
     */
    fun fail(error: String): Conversation {
        return copy(
            turn = null,
            updatedAt = Instant.now()
        )
    }

    /**
     * Check if there are pending tool calls.
     */
    fun hasPendingTools(): Boolean = turn?.hasPendingTools() ?: false

    /**
     * Get the pending tool calls.
     */
    fun pendingTools(): List<ToolCall> = turn?.pendingToolCalls ?: emptyList()

    /**
     * Update the AI session ID.
     */
    fun withAiSessionId(sessionId: String): Conversation =
        copy(aiSessionId = sessionId, updatedAt = Instant.now())
}
