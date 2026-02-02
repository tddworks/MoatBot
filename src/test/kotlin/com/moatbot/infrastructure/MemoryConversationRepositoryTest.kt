package com.moatbot.infrastructure

import com.moatbot.domain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MemoryConversationRepositoryTest {

    private val repository = MemoryConversationRepository()

    @Test
    fun `findByKey returns null when no conversation exists`() = runTest {
        val key = SessionKey("user:chat")

        val result = repository.findByKey(key)

        assertNull(result)
    }

    @Test
    fun `save and findByKey round trip`() = runTest {
        val key = SessionKey("user:chat")
        val conversation = Conversation.start(key)

        repository.save(conversation)
        val result = repository.findByKey(key)

        assertNotNull(result)
        assertEquals(key, result.key)
    }

    @Test
    fun `save overwrites existing conversation`() = runTest {
        val key = SessionKey("user:chat")
        val conversation1 = Conversation.start(key)
        val conversation2 = conversation1.receive(UserId("user"), "Hello")

        repository.save(conversation1)
        repository.save(conversation2)
        val result = repository.findByKey(key)

        assertEquals(1, result?.messages?.size)
    }

    @Test
    fun `delete removes conversation`() = runTest {
        val key = SessionKey("user:chat")
        val conversation = Conversation.start(key)

        repository.save(conversation)
        repository.delete(key)
        val result = repository.findByKey(key)

        assertNull(result)
    }

    @Test
    fun `delete does not fail when conversation does not exist`() = runTest {
        val key = SessionKey("nonexistent")

        repository.delete(key)

        assertNull(repository.findByKey(key))
    }

    @Test
    fun `different keys store different conversations`() = runTest {
        val key1 = SessionKey("user1:chat")
        val key2 = SessionKey("user2:chat")
        val conversation1 = Conversation.start(key1)
        val conversation2 = Conversation.start(key2)
            .receive(UserId("user2"), "Hello")

        repository.save(conversation1)
        repository.save(conversation2)

        val result1 = repository.findByKey(key1)
        val result2 = repository.findByKey(key2)

        assertEquals(0, result1?.messages?.size)
        assertEquals(1, result2?.messages?.size)
    }

    @Test
    fun `saves conversation with messages`() = runTest {
        val key = SessionKey("user:chat")
        val conversation = Conversation.start(key)
            .receive(UserId("user"), "Hello")
            .addTextChunk("Hi!")
            .complete("Hi!")

        repository.save(conversation)
        val result = repository.findByKey(key)

        assertNotNull(result)
        assertEquals(2, result.messages.size)
    }

    @Test
    fun `saves conversation with AI session ID`() = runTest {
        val key = SessionKey("user:chat")
        val conversation = Conversation.start(key)
            .withAiSessionId("session-abc-123")

        repository.save(conversation)
        val result = repository.findByKey(key)

        assertEquals("session-abc-123", result?.aiSessionId)
    }
}
