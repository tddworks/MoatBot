package com.moatbot.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.time.Instant

class MessageTest {

    @Test
    fun `User message contains content and metadata`() {
        val userId = UserId("user-123")
        val timestamp = Instant.now()

        val message = Message.User(
            id = MessageId("msg-001"),
            userId = userId,
            content = "Hello, Claude!",
            timestamp = timestamp
        )

        assertEquals("Hello, Claude!", message.content)
        assertEquals(userId, message.userId)
        assertEquals(timestamp, message.timestamp)
    }

    @Test
    fun `Assistant message contains text response`() {
        val timestamp = Instant.now()

        val message = Message.Assistant(
            id = MessageId("msg-002"),
            content = "Hello! How can I help you?",
            timestamp = timestamp
        )

        assertEquals("Hello! How can I help you?", message.content)
        assertEquals(timestamp, message.timestamp)
    }

    @Test
    fun `AssistantToolUse message contains tool calls`() {
        val toolCall = ToolCall(
            id = ToolCallId("toolu_001"),
            name = "read_file",
            arguments = mapOf("path" to "/tmp/test.txt")
        )
        val timestamp = Instant.now()

        val message = Message.AssistantToolUse(
            id = MessageId("msg-003"),
            toolCalls = listOf(toolCall),
            timestamp = timestamp
        )

        assertEquals(1, message.toolCalls.size)
        assertEquals("read_file", message.toolCalls[0].name)
        assertEquals(ToolCallId("toolu_001"), message.toolCalls[0].id)
    }

    @Test
    fun `ToolResult message links to tool call`() {
        val toolCallId = ToolCallId("toolu_001")
        val timestamp = Instant.now()

        val message = Message.ToolResult(
            id = MessageId("msg-004"),
            toolCallId = toolCallId,
            content = "File contents here",
            isError = false,
            timestamp = timestamp
        )

        assertEquals(toolCallId, message.toolCallId)
        assertEquals("File contents here", message.content)
        assertEquals(false, message.isError)
    }

    @Test
    fun `ToolResult can indicate error`() {
        val toolCallId = ToolCallId("toolu_001")

        val message = Message.ToolResult(
            id = MessageId("msg-005"),
            toolCallId = toolCallId,
            content = "File not found",
            isError = true,
            timestamp = Instant.now()
        )

        assertTrue(message.isError)
    }

    @Test
    fun `Message sealed class allows pattern matching`() {
        val messages: List<Message> = listOf(
            Message.User(MessageId("1"), UserId("u1"), "Hello", Instant.now()),
            Message.Assistant(MessageId("2"), "Hi!", Instant.now()),
            Message.AssistantToolUse(
                MessageId("3"),
                listOf(ToolCall(ToolCallId("t1"), "test", emptyMap())),
                Instant.now()
            ),
            Message.ToolResult(MessageId("4"), ToolCallId("t1"), "result", false, Instant.now())
        )

        val types = messages.map { msg ->
            when (msg) {
                is Message.User -> "user"
                is Message.Assistant -> "assistant"
                is Message.AssistantToolUse -> "tool_use"
                is Message.ToolResult -> "tool_result"
            }
        }

        assertEquals(listOf("user", "assistant", "tool_use", "tool_result"), types)
    }

    @Test
    fun `ToolCall contains name and arguments`() {
        val toolCall = ToolCall(
            id = ToolCallId("toolu_abc"),
            name = "search_files",
            arguments = mapOf(
                "pattern" to "*.kt",
                "directory" to "/src"
            )
        )

        assertEquals("search_files", toolCall.name)
        assertEquals("*.kt", toolCall.arguments["pattern"])
        assertEquals("/src", toolCall.arguments["directory"])
    }

    @Test
    fun `User message can be created with factory method`() {
        val userId = UserId("user-123")
        val message = Message.User.create(userId, "Hello!")

        assertEquals("Hello!", message.content)
        assertEquals(userId, message.userId)
    }

    @Test
    fun `Assistant message can be created with factory method`() {
        val message = Message.Assistant.create("Hello!")

        assertEquals("Hello!", message.content)
    }
}
