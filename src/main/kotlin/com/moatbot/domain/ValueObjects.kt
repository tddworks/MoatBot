package com.moatbot.domain

import kotlinx.serialization.Serializable
import java.util.UUID

@JvmInline
@Serializable
value class ConversationId(val value: String) {
    companion object {
        fun generate(): ConversationId = ConversationId(UUID.randomUUID().toString())
    }
}

@JvmInline
@Serializable
value class UserId(val value: String)

@JvmInline
@Serializable
value class ChatId(val value: String)

@JvmInline
@Serializable
value class SessionKey(val value: String) {
    companion object {
        fun from(userId: UserId, chatId: ChatId): SessionKey =
            SessionKey("${userId.value}:${chatId.value}")
    }
}

@JvmInline
@Serializable
value class ToolCallId(val value: String) {
    companion object {
        fun generate(): ToolCallId = ToolCallId(UUID.randomUUID().toString())
    }
}

@JvmInline
@Serializable
value class MessageId(val value: String) {
    companion object {
        fun generate(): MessageId = MessageId(UUID.randomUUID().toString())
    }
}
