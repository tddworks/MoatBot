package com.moatbot.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ValueObjectsTest {

    @Test
    fun `UserId wraps string value`() {
        val userId = UserId("user-123")
        assertEquals("user-123", userId.value)
    }

    @Test
    fun `UserId equality based on value`() {
        val userId1 = UserId("user-123")
        val userId2 = UserId("user-123")
        val userId3 = UserId("user-456")

        assertEquals(userId1, userId2)
        assertNotEquals(userId1, userId3)
    }

    @Test
    fun `ChatId wraps string value`() {
        val chatId = ChatId("chat-456")
        assertEquals("chat-456", chatId.value)
    }

    @Test
    fun `ChatId equality based on value`() {
        val chatId1 = ChatId("chat-456")
        val chatId2 = ChatId("chat-456")
        val chatId3 = ChatId("chat-789")

        assertEquals(chatId1, chatId2)
        assertNotEquals(chatId1, chatId3)
    }

    @Test
    fun `SessionKey wraps string value`() {
        val sessionKey = SessionKey("session-abc")
        assertEquals("session-abc", sessionKey.value)
    }

    @Test
    fun `SessionKey equality based on value`() {
        val sessionKey1 = SessionKey("session-abc")
        val sessionKey2 = SessionKey("session-abc")
        val sessionKey3 = SessionKey("session-xyz")

        assertEquals(sessionKey1, sessionKey2)
        assertNotEquals(sessionKey1, sessionKey3)
    }

    @Test
    fun `SessionKey can be created from UserId and ChatId`() {
        val userId = UserId("user-123")
        val chatId = ChatId("chat-456")
        val sessionKey = SessionKey.from(userId, chatId)

        assertEquals("user-123:chat-456", sessionKey.value)
    }

    @Test
    fun `ToolCallId wraps string value`() {
        val toolCallId = ToolCallId("toolu_123")
        assertEquals("toolu_123", toolCallId.value)
    }

    @Test
    fun `MessageId wraps string value`() {
        val messageId = MessageId("msg-001")
        assertEquals("msg-001", messageId.value)
    }

    @Test
    fun `ConversationId wraps string value`() {
        val conversationId = ConversationId("conv-001")
        assertEquals("conv-001", conversationId.value)
    }

    @Test
    fun `ConversationId generate creates unique ids`() {
        val id1 = ConversationId.generate()
        val id2 = ConversationId.generate()
        assertNotEquals(id1, id2)
    }
}
