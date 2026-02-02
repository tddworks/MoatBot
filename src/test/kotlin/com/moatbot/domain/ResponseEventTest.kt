package com.moatbot.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ResponseEventTest {

    @Test
    fun `TextChunk contains text`() {
        val event = ResponseEvent.TextChunk("Hello")
        assertEquals("Hello", event.text)
    }

    @Test
    fun `ToolStarted contains tool call`() {
        val toolCall = ToolCall.create("test_tool", mapOf("arg" to "value"))
        val event = ResponseEvent.ToolStarted(toolCall)

        assertEquals(toolCall, event.call)
        assertEquals("test_tool", event.call.name)
    }

    @Test
    fun `ToolCompleted contains call id and result`() {
        val callId = ToolCallId("call-123")
        val event = ResponseEvent.ToolCompleted(callId, "tool result")

        assertEquals(callId, event.callId)
        assertEquals("tool result", event.result)
    }

    @Test
    fun `Completed contains response text`() {
        val event = ResponseEvent.Completed("Full response text")
        assertEquals("Full response text", event.response)
    }

    @Test
    fun `Failed contains error message`() {
        val event = ResponseEvent.Failed("Something went wrong")
        assertEquals("Something went wrong", event.error)
    }

    @Test
    fun `ResponseEvent allows pattern matching`() {
        val events: List<ResponseEvent> = listOf(
            ResponseEvent.TextChunk("chunk"),
            ResponseEvent.ToolStarted(ToolCall.create("tool", emptyMap())),
            ResponseEvent.ToolCompleted(ToolCallId("id"), "result"),
            ResponseEvent.Completed("done"),
            ResponseEvent.Failed("error")
        )

        val types = events.map { event ->
            when (event) {
                is ResponseEvent.TextChunk -> "text"
                is ResponseEvent.ToolStarted -> "tool_started"
                is ResponseEvent.ToolCompleted -> "tool_completed"
                is ResponseEvent.Completed -> "completed"
                is ResponseEvent.Failed -> "failed"
            }
        }

        assertEquals(
            listOf("text", "tool_started", "tool_completed", "completed", "failed"),
            types
        )
    }

    @Test
    fun `ResponseEvent subtypes are correctly identified`() {
        assertIs<ResponseEvent.TextChunk>(ResponseEvent.TextChunk("test"))
        assertIs<ResponseEvent.Completed>(ResponseEvent.Completed("test"))
        assertIs<ResponseEvent.Failed>(ResponseEvent.Failed("error"))
    }
}
