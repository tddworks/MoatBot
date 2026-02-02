package com.moatbot.infrastructure

import com.moatbot.app.ConversationStatus
import com.moatbot.app.Moatbot
import com.moatbot.domain.ChatId
import com.moatbot.domain.SessionKey
import com.moatbot.domain.UserId
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscordSlashCommandHandlerTest {

    private val moatbot = mock<Moatbot>()
    private val handler = DiscordSlashCommandHandler(moatbot)

    private val userId = UserId("user-123")
    private val chatId = ChatId("chat-456")

    @Test
    fun `isCommand returns true for valid commands`() {
        assertTrue(handler.isCommand("/new"))
        assertTrue(handler.isCommand("/clear"))
        assertTrue(handler.isCommand("/help"))
        assertTrue(handler.isCommand("/status"))
    }

    @Test
    fun `isCommand is case insensitive`() {
        assertTrue(handler.isCommand("/NEW"))
        assertTrue(handler.isCommand("/Clear"))
        assertTrue(handler.isCommand("/HELP"))
    }

    @Test
    fun `isCommand returns false for non-commands`() {
        assertFalse(handler.isCommand("hello"))
        assertFalse(handler.isCommand("new"))
        assertFalse(handler.isCommand("/unknown"))
    }

    @Test
    fun `new command clears session and returns success message`() = runTest {
        val response = handler.handleCommand("/new", userId, chatId)

        verify(moatbot).clear(SessionKey.from(userId, chatId))
        assertTrue(response.contains("New session started"))
    }

    @Test
    fun `clear command clears session and returns success message`() = runTest {
        val response = handler.handleCommand("/clear", userId, chatId)

        verify(moatbot).clear(SessionKey.from(userId, chatId))
        assertTrue(response.contains("Session cleared"))
    }

    @Test
    fun `help command returns help message with all commands`() = runTest {
        val response = handler.handleCommand("/help", userId, chatId)

        assertTrue(response.contains("/new"))
        assertTrue(response.contains("/clear"))
        assertTrue(response.contains("/status"))
        assertTrue(response.contains("/help"))
    }

    @Test
    fun `status command returns session info when session exists`() = runTest {
        val status = ConversationStatus(
            messageCount = 5,
            aiSessionId = "session-abc123",
            createdAt = Instant.parse("2024-01-15T10:30:00Z")
        )
        whenever(moatbot.status(SessionKey.from(userId, chatId))).thenReturn(status)

        val response = handler.handleCommand("/status", userId, chatId)

        assertTrue(response.contains("5"))
        assertTrue(response.contains("session-abc123".take(20)))
    }

    @Test
    fun `status command returns no session message when session does not exist`() = runTest {
        whenever(moatbot.status(SessionKey.from(userId, chatId))).thenReturn(null)

        val response = handler.handleCommand("/status", userId, chatId)

        assertTrue(response.contains("No active session"))
    }

    @Test
    fun `commands are case insensitive`() = runTest {
        handler.handleCommand("/NEW", userId, chatId)
        verify(moatbot).clear(any())

        reset(moatbot)

        handler.handleCommand("/Clear", userId, chatId)
        verify(moatbot).clear(any())
    }

    @Test
    fun `unknown command returns error message`() = runTest {
        val response = handler.handleCommand("/unknown", userId, chatId)

        assertTrue(response.contains("Unknown command"))
        verify(moatbot, never()).clear(any())
    }
}