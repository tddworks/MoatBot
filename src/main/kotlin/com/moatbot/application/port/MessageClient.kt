package com.moatbot.application.port

import com.moatbot.domain.ChatId
import com.moatbot.domain.UserId
import kotlinx.coroutines.flow.Flow

data class IncomingMessage(
    val userId: UserId,
    val chatId: ChatId,
    val content: String,
    val platform: Platform
)

enum class Platform {
    TELEGRAM,
    DISCORD
}

interface MessageClient {
    fun messages(): Flow<IncomingMessage>
    suspend fun sendMessage(chatId: ChatId, content: String)
    suspend fun sendTypingIndicator(chatId: ChatId)
    suspend fun start()
    suspend fun stop()

    /**
     * Whether this client supports editable messages (placeholder pattern).
     */
    fun supportsEditableMessages(): Boolean = false

    /**
     * Send a placeholder message that can be edited later.
     * Returns the message ID, or null if not supported.
     */
    suspend fun sendPlaceholderMessage(chatId: ChatId, placeholder: String = "Thinking..."): String? = null

    /**
     * Edit an existing message with new content.
     */
    suspend fun editMessage(chatId: ChatId, messageId: String, content: String) {}

    /**
     * Stop the typing indicator for a channel.
     */
    suspend fun stopTypingIndicator(chatId: ChatId) {}
}
