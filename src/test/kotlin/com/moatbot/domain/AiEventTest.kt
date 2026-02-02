package com.moatbot.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AiEventTest {

    @Test
    fun `Text event contains text`() {
        val event = AiEvent.Text("Hello world")
        assertEquals("Hello world", event.text)
    }

    @Test
    fun `ToolCall event contains tool call`() {
        val toolCall = ToolCall.create("read_file", mapOf("path" to "/tmp/test.txt"))
        val event = AiEvent.ToolCall(toolCall)

        assertEquals(toolCall, event.call)
        assertEquals("read_file", event.call.name)
    }

    @Test
    fun `SessionId event contains id`() {
        val event = AiEvent.SessionId("session-abc-123")
        assertEquals("session-abc-123", event.id)
    }

    @Test
    fun `Done is singleton`() {
        val event1 = AiEvent.Done
        val event2 = AiEvent.Done
        assertEquals(event1, event2)
    }

    @Test
    fun `Error event contains message`() {
        val event = AiEvent.Error("Something went wrong")
        assertEquals("Something went wrong", event.message)
    }

    @Test
    fun `AiEvent allows pattern matching`() {
        val events: List<AiEvent> = listOf(
            AiEvent.Text("hello"),
            AiEvent.ToolCall(ToolCall.create("tool", emptyMap())),
            AiEvent.SessionId("session-id"),
            AiEvent.Done,
            AiEvent.Error("error")
        )

        val types = events.map { event ->
            when (event) {
                is AiEvent.Text -> "text"
                is AiEvent.ToolCall -> "tool_call"
                is AiEvent.SessionId -> "session_id"
                is AiEvent.Done -> "done"
                is AiEvent.Error -> "error"
            }
        }

        assertEquals(
            listOf("text", "tool_call", "session_id", "done", "error"),
            types
        )
    }

    @Test
    fun `AiEvent subtypes are correctly identified`() {
        assertIs<AiEvent.Text>(AiEvent.Text("test"))
        assertIs<AiEvent.Done>(AiEvent.Done)
        assertIs<AiEvent.Error>(AiEvent.Error("error"))
    }
}
