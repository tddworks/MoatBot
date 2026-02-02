package com.moatbot.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TurnTest {

    @Test
    fun `Turn starts with empty text`() {
        val turn = Turn()
        assertEquals("", turn.text)
    }

    @Test
    fun `Turn can append text`() {
        val turn = Turn()
            .appendText("Hello ")
            .appendText("World!")

        assertEquals("Hello World!", turn.text)
    }

    @Test
    fun `Turn starts with no pending tools`() {
        val turn = Turn()
        assertFalse(turn.hasPendingTools())
        assertTrue(turn.pendingToolCalls.isEmpty())
    }

    @Test
    fun `Turn can add tool call`() {
        val toolCall = ToolCall.create("test_tool", mapOf("arg" to "value"))
        val turn = Turn().addToolCall(toolCall)

        assertTrue(turn.hasPendingTools())
        assertEquals(1, turn.pendingToolCalls.size)
        assertEquals(toolCall, turn.pendingToolCalls[0])
    }

    @Test
    fun `Turn can add multiple tool calls`() {
        val toolCall1 = ToolCall.create("tool1", emptyMap())
        val toolCall2 = ToolCall.create("tool2", emptyMap())

        val turn = Turn()
            .addToolCall(toolCall1)
            .addToolCall(toolCall2)

        assertEquals(2, turn.pendingToolCalls.size)
    }

    @Test
    fun `Turn can complete tool call`() {
        val toolCall = ToolCall.create("test_tool", emptyMap())
        val turn = Turn()
            .addToolCall(toolCall)
            .completeToolCall(toolCall.id, "result")

        assertFalse(turn.hasPendingTools())
        assertEquals(1, turn.completedToolCalls.size)
        assertEquals(toolCall.id, turn.completedToolCalls[0].callId)
        assertEquals("result", turn.completedToolCalls[0].result)
    }

    @Test
    fun `Turn tracks completed tool calls`() {
        val toolCall1 = ToolCall.create("tool1", emptyMap())
        val toolCall2 = ToolCall.create("tool2", emptyMap())

        val turn = Turn()
            .addToolCall(toolCall1)
            .addToolCall(toolCall2)
            .completeToolCall(toolCall1.id, "result1")
            .completeToolCall(toolCall2.id, "result2")

        assertEquals(0, turn.pendingToolCalls.size)
        assertEquals(2, turn.completedToolCalls.size)
    }

    @Test
    fun `Turn is immutable`() {
        val original = Turn()
        val withText = original.appendText("Hello")

        assertEquals("", original.text)
        assertEquals("Hello", withText.text)
    }

    @Test
    fun `Completing unknown tool call does not fail`() {
        val turn = Turn()
            .completeToolCall(ToolCallId("unknown"), "result")

        assertEquals(1, turn.completedToolCalls.size)
        assertTrue(turn.pendingToolCalls.isEmpty())
    }
}
