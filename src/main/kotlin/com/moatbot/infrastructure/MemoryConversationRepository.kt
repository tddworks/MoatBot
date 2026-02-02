package com.moatbot.infrastructure

import com.moatbot.domain.Conversation
import com.moatbot.domain.ConversationRepository
import com.moatbot.domain.SessionKey
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of ConversationRepository.
 * Useful for testing and development.
 */
class MemoryConversationRepository : ConversationRepository {

    private val conversations = ConcurrentHashMap<SessionKey, Conversation>()

    override suspend fun findByKey(key: SessionKey): Conversation? = conversations[key]

    override suspend fun save(conversation: Conversation) {
        conversations[conversation.key] = conversation
    }

    override suspend fun delete(key: SessionKey) {
        conversations.remove(key)
    }
}
