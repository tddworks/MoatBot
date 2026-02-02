package com.moatbot.domain

/**
 * Repository for storing and retrieving conversations.
 */
interface ConversationRepository {
    /**
     * Find a conversation by its session key.
     */
    suspend fun findByKey(key: SessionKey): Conversation?

    /**
     * Save a conversation.
     */
    suspend fun save(conversation: Conversation)

    /**
     * Delete a conversation by its session key.
     */
    suspend fun delete(key: SessionKey)
}
