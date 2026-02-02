package com.moatbot.domain

import kotlinx.serialization.Serializable
import java.time.Instant

sealed class Message {
    abstract val id: MessageId
    abstract val timestamp: Instant

    data class User(
        override val id: MessageId,
        val userId: UserId,
        val content: String,
        override val timestamp: Instant
    ) : Message() {
        companion object {
            fun create(userId: UserId, content: String): User =
                User(
                    id = MessageId.generate(),
                    userId = userId,
                    content = content,
                    timestamp = Instant.now()
                )
        }
    }

    data class Assistant(
        override val id: MessageId,
        val content: String,
        override val timestamp: Instant
    ) : Message() {
        companion object {
            fun create(content: String): Assistant =
                Assistant(
                    id = MessageId.generate(),
                    content = content,
                    timestamp = Instant.now()
                )
        }
    }

    data class AssistantToolUse(
        override val id: MessageId,
        val toolCalls: List<ToolCall>,
        override val timestamp: Instant
    ) : Message() {
        companion object {
            fun create(toolCalls: List<ToolCall>): AssistantToolUse =
                AssistantToolUse(
                    id = MessageId.generate(),
                    toolCalls = toolCalls,
                    timestamp = Instant.now()
                )
        }
    }

    data class ToolResult(
        override val id: MessageId,
        val toolCallId: ToolCallId,
        val content: String,
        val isError: Boolean,
        override val timestamp: Instant
    ) : Message() {
        companion object {
            fun create(toolCallId: ToolCallId, content: String, isError: Boolean = false): ToolResult =
                ToolResult(
                    id = MessageId.generate(),
                    toolCallId = toolCallId,
                    content = content,
                    isError = isError,
                    timestamp = Instant.now()
                )
        }
    }
}
