package com.moatbot.infrastructure

import com.moatbot.domain.*
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileConversationRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repository: FileConversationRepository

    @BeforeTest
    fun setup() {
        tempDir = File.createTempFile("moatbot-test", "").also {
            it.delete()
            it.mkdirs()
        }
        repository = FileConversationRepository(tempDir.absolutePath)
    }

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `findByKey returns null when no conversation exists`() = runTest {
        val key = SessionKey("user:chat")

        val result = repository.findByKey(key)

        assertNull(result)
    }

    @Test
    fun `save creates file`() = runTest {
        val key = SessionKey("user:chat")
        val conversation = Conversation.start(key)

        repository.save(conversation)

        assertTrue(tempDir.listFiles()?.isNotEmpty() == true)
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
    fun `save persists messages`() = runTest {
        val key = SessionKey("user:chat")
        val userId = UserId("user-1")
        val conversation = Conversation.start(key)
            .receive(userId, "Hello!")
            .addTextChunk("Hi there!")
            .complete("Hi there!")

        repository.save(conversation)
        val result = repository.findByKey(key)

        assertNotNull(result)
        assertEquals(2, result.messages.size)

        val userMessage = result.messages[0] as Message.User
        assertEquals("Hello!", userMessage.content)
        assertEquals(userId, userMessage.userId)

        val assistantMessage = result.messages[1] as Message.Assistant
        assertEquals("Hi there!", assistantMessage.content)
    }

    @Test
    fun `save persists AI session ID`() = runTest {
        val key = SessionKey("user:chat")
        val conversation = Conversation.start(key)
            .withAiSessionId("session-xyz-789")

        repository.save(conversation)
        val result = repository.findByKey(key)

        assertEquals("session-xyz-789", result?.aiSessionId)
    }

    @Test
    fun `save persists tool use messages`() = runTest {
        val key = SessionKey("user:chat")
        val toolCall = ToolCall(
            id = ToolCallId("toolu_123"),
            name = "read_file",
            arguments = mapOf("path" to "/tmp/test.txt")
        )

        val conversation = Conversation.start(key)
            .receive(UserId("user"), "Read file")

        // Manually create a conversation with tool use message
        val withToolUse = conversation.copy(
            messages = conversation.messages + Message.AssistantToolUse.create(listOf(toolCall))
        )

        repository.save(withToolUse)
        val result = repository.findByKey(key)

        assertNotNull(result)
        assertEquals(2, result.messages.size)

        val toolUseMessage = result.messages[1] as Message.AssistantToolUse
        assertEquals(1, toolUseMessage.toolCalls.size)
        assertEquals("read_file", toolUseMessage.toolCalls[0].name)
    }

    @Test
    fun `save persists tool result messages`() = runTest {
        val key = SessionKey("user:chat")
        val toolCallId = ToolCallId("toolu_456")

        val conversation = Conversation.start(key)
            .receive(UserId("user"), "Use tool")

        val withToolResult = conversation.copy(
            messages = conversation.messages + Message.ToolResult.create(
                toolCallId = toolCallId,
                content = "Tool output here",
                isError = false
            )
        )

        repository.save(withToolResult)
        val result = repository.findByKey(key)

        val toolResult = result?.messages?.get(1) as? Message.ToolResult
        assertNotNull(toolResult)
        assertEquals(toolCallId, toolResult.toolCallId)
        assertEquals("Tool output here", toolResult.content)
        assertEquals(false, toolResult.isError)
    }

    @Test
    fun `delete removes file`() = runTest {
        val key = SessionKey("user:chat")
        val conversation = Conversation.start(key)

        repository.save(conversation)
        repository.delete(key)

        assertNull(repository.findByKey(key))
        assertTrue(tempDir.listFiles()?.isEmpty() != false)
    }

    @Test
    fun `delete does not fail when file does not exist`() = runTest {
        val key = SessionKey("nonexistent")

        repository.delete(key)

        assertNull(repository.findByKey(key))
    }

    @Test
    fun `handles special characters in session key`() = runTest {
        val key = SessionKey("user@test.com:channel#123")
        val conversation = Conversation.start(key)

        repository.save(conversation)
        val result = repository.findByKey(key)

        assertNotNull(result)
        assertEquals(key, result.key)
    }

    @Test
    fun `preserves timestamps`() = runTest {
        val key = SessionKey("user:chat")
        val conversation = Conversation.start(key)

        repository.save(conversation)
        val result = repository.findByKey(key)

        assertNotNull(result)
        assertEquals(conversation.createdAt, result.createdAt)
        assertEquals(conversation.updatedAt, result.updatedAt)
    }

    @Test
    fun `preserves conversation ID`() = runTest {
        val key = SessionKey("user:chat")
        val conversation = Conversation.start(key)

        repository.save(conversation)
        val result = repository.findByKey(key)

        assertNotNull(result)
        assertEquals(conversation.id, result.id)
    }
}
