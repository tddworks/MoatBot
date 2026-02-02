package com.moatbot.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant

class ConversationTest {

    @Test
    fun `Conversation is created with key and empty messages`() {
        val sessionKey = SessionKey("user-1:chat-1")
        val conversation = Conversation.start(sessionKey)

        assertEquals(sessionKey, conversation.key)
        assertTrue(conversation.messages.isEmpty())
        assertNull(conversation.turn)
    }

    @Test
    fun `Conversation can receive user message`() {
        val sessionKey = SessionKey("user-1:chat-1")
        val conversation = Conversation.start(sessionKey)

        val updated = conversation.receive(UserId("user-1"), "Hello!")

        assertEquals(1, updated.messages.size)
        assertTrue(updated.messages[0] is Message.User)
        assertEquals("Hello!", (updated.messages[0] as Message.User).content)
        assertNotNull(updated.turn)
    }

    @Test
    fun `Conversation is immutable - receiving message returns new conversation`() {
        val sessionKey = SessionKey("user-1:chat-1")
        val conversation = Conversation.start(sessionKey)

        val updated = conversation.receive(UserId("user-1"), "Hello!")

        assertTrue(conversation.messages.isEmpty())
        assertEquals(1, updated.messages.size)
    }

    @Test
    fun `Conversation can add text chunks during turn`() {
        val conversation = Conversation.start(SessionKey("key"))
            .receive(UserId("user"), "Hello")

        val withChunk1 = conversation.addTextChunk("Hi ")
        val withChunk2 = withChunk1.addTextChunk("there!")

        assertEquals("Hi there!", withChunk2.turn?.text)
    }

    @Test
    fun `Conversation can add and complete tool calls`() {
        val conversation = Conversation.start(SessionKey("key"))
            .receive(UserId("user"), "Hello")

        val toolCall = ToolCall.create("test_tool", mapOf("arg" to "value"))

        val withTool = conversation.addToolCall(toolCall)
        assertTrue(withTool.hasPendingTools())
        assertEquals(1, withTool.pendingTools().size)

        val completed = withTool.addToolResult(toolCall.id, "result")
        assertTrue(!completed.hasPendingTools())
    }

    @Test
    fun `Conversation can complete turn`() {
        val conversation = Conversation.start(SessionKey("key"))
            .receive(UserId("user"), "Hello")
            .addTextChunk("Response text")
            .complete("Response text")

        assertNull(conversation.turn)
        assertEquals(2, conversation.messages.size)
        assertTrue(conversation.messages[1] is Message.Assistant)
        assertEquals("Response text", (conversation.messages[1] as Message.Assistant).content)
    }

    @Test
    fun `Conversation can fail turn`() {
        val conversation = Conversation.start(SessionKey("key"))
            .receive(UserId("user"), "Hello")
            .fail("Some error")

        assertNull(conversation.turn)
        assertEquals(1, conversation.messages.size)
    }

    @Test
    fun `Conversation can update AI session ID`() {
        val conversation = Conversation.start(SessionKey("key"))
        assertNull(conversation.aiSessionId)

        val updated = conversation.withAiSessionId("new-session-id")

        assertEquals("new-session-id", updated.aiSessionId)
        assertNull(conversation.aiSessionId) // Original unchanged
    }

    @Test
    fun `Conversation tracks creation and update timestamps`() {
        val before = Instant.now()
        val conversation = Conversation.start(SessionKey("key"))
        val after = Instant.now()

        assertTrue(conversation.createdAt >= before)
        assertTrue(conversation.createdAt <= after)
        assertEquals(conversation.createdAt, conversation.updatedAt)
    }

    @Test
    fun `Conversation updatedAt changes when message received`() {
        val conversation = Conversation.start(SessionKey("key"))
        val originalUpdatedAt = conversation.updatedAt

        Thread.sleep(10) // Ensure time difference

        val updated = conversation.receive(UserId("user"), "Hello")

        assertTrue(updated.updatedAt > originalUpdatedAt)
    }
}
