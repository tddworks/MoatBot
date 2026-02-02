package com.moatbot.app

import com.moatbot.domain.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MoatbotTest {

    private val aiProvider = mock<AiProvider>()
    private val conversationRepository = mock<ConversationRepository>()
    private val toolRunner = mock<ToolRunner>()

    private val moatbot = Moatbot(aiProvider, conversationRepository, toolRunner)

    private val sessionKey = SessionKey("user:chat")
    private val userId = UserId("user-1")

    @Test
    fun `chat creates new conversation when none exists`() = runTest {
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Text("Hello!"))
            emit(AiEvent.Done)
        })

        moatbot.chat(sessionKey, userId, "Hi").toList()

        verify(conversationRepository).findByKey(sessionKey)
        verify(conversationRepository, times(2)).save(argThat { key == sessionKey })
    }

    @Test
    fun `chat uses existing conversation`() = runTest {
        val existingConversation = Conversation.start(sessionKey)
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(existingConversation)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Text("Hello!"))
            emit(AiEvent.Done)
        })

        moatbot.chat(sessionKey, userId, "Hi").toList()

        verify(conversationRepository).findByKey(sessionKey)
    }

    @Test
    fun `chat emits text chunks`() = runTest {
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Text("Hello "))
            emit(AiEvent.Text("World!"))
            emit(AiEvent.Done)
        })

        val events = moatbot.chat(sessionKey, userId, "Hi").toList()

        assertEquals(3, events.size)
        assertIs<ResponseEvent.TextChunk>(events[0])
        assertEquals("Hello ", (events[0] as ResponseEvent.TextChunk).text)
        assertIs<ResponseEvent.TextChunk>(events[1])
        assertEquals("World!", (events[1] as ResponseEvent.TextChunk).text)
        assertIs<ResponseEvent.Completed>(events[2])
    }

    @Test
    fun `chat emits completed with final text`() = runTest {
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Text("Hello World!"))
            emit(AiEvent.Done)
        })

        val events = moatbot.chat(sessionKey, userId, "Hi").toList()

        val completed = events.last()
        assertIs<ResponseEvent.Completed>(completed)
        assertEquals("Hello World!", completed.response)
    }

    @Test
    fun `chat handles tool calls`() = runTest {
        val toolCall = ToolCall.create("test_tool", mapOf("arg" to "value"))

        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.ToolCall(toolCall))
            emit(AiEvent.Text("Tool result processed"))
            emit(AiEvent.Done)
        })
        whenever(toolRunner.run(toolCall)).thenReturn(ToolResult("tool output"))

        val events = moatbot.chat(sessionKey, userId, "Use tool").toList()

        verify(toolRunner).run(toolCall)

        assertIs<ResponseEvent.ToolStarted>(events[0])
        assertEquals(toolCall, (events[0] as ResponseEvent.ToolStarted).call)

        assertIs<ResponseEvent.ToolCompleted>(events[1])
        assertEquals(toolCall.id, (events[1] as ResponseEvent.ToolCompleted).callId)
        assertEquals("tool output", (events[1] as ResponseEvent.ToolCompleted).result)
    }

    @Test
    fun `chat handles AI errors`() = runTest {
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Error("AI provider failed"))
        })

        val events = moatbot.chat(sessionKey, userId, "Hi").toList()

        assertEquals(1, events.size)
        assertIs<ResponseEvent.Failed>(events[0])
        assertEquals("AI provider failed", (events[0] as ResponseEvent.Failed).error)
    }

    @Test
    fun `chat handles exceptions`() = runTest {
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            throw RuntimeException("Unexpected error")
        })

        val events = moatbot.chat(sessionKey, userId, "Hi").toList()

        assertEquals(1, events.size)
        assertIs<ResponseEvent.Failed>(events[0])
        assertEquals("Unexpected error", (events[0] as ResponseEvent.Failed).error)
    }

    @Test
    fun `chat saves conversation with AI session ID`() = runTest {
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.SessionId("new-session-id"))
            emit(AiEvent.Text("Hello!"))
            emit(AiEvent.Done)
        })

        moatbot.chat(sessionKey, userId, "Hi").toList()

        verify(conversationRepository, atLeast(1)).save(argThat {
            aiSessionId == "new-session-id"
        })
    }

    @Test
    fun `chat passes session ID to AI provider`() = runTest {
        val existingConversation = Conversation.start(sessionKey).withAiSessionId("existing-session")
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(existingConversation)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Text("Hello!"))
            emit(AiEvent.Done)
        })

        moatbot.chat(sessionKey, userId, "Hi").toList()

        verify(aiProvider).complete(any(), eq("existing-session"))
    }

    @Test
    fun `clear deletes conversation`() = runTest {
        moatbot.clear(sessionKey)

        verify(conversationRepository).delete(sessionKey)
    }

    @Test
    fun `status returns null when no conversation exists`() = runTest {
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)

        val status = moatbot.status(sessionKey)

        assertNull(status)
    }

    @Test
    fun `status returns conversation info`() = runTest {
        val conversation = Conversation.start(sessionKey)
            .receive(userId, "Hello")
            .withAiSessionId("session-123")

        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(conversation)

        val status = moatbot.status(sessionKey)

        assertEquals(1, status?.messageCount)
        assertEquals("session-123", status?.aiSessionId)
    }

    @Test
    fun `chat saves conversation after receiving user message`() = runTest {
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Text("Hello!"))
            emit(AiEvent.Done)
        })

        moatbot.chat(sessionKey, userId, "Hi").toList()

        verify(conversationRepository, atLeast(1)).save(argThat {
            messages.size == 1 && messages[0] is Message.User
        })
    }

    @Test
    fun `chat saves conversation after completion`() = runTest {
        whenever(conversationRepository.findByKey(sessionKey)).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Text("Hello!"))
            emit(AiEvent.Done)
        })

        moatbot.chat(sessionKey, userId, "Hi").toList()

        verify(conversationRepository, atLeast(1)).save(argThat {
            messages.any { it is Message.Assistant }
        })
    }
}
