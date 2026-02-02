package com.moatbot.infrastructure

import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.StopReason
import com.moatbot.app.Moatbot
import com.moatbot.domain.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AcpAgentTest {

    private val aiProvider = mock<AiProvider>()
    private val conversationRepository = mock<ConversationRepository>()
    private val toolRunner = mock<ToolRunner>()
    private val moatbot = Moatbot(aiProvider, conversationRepository, toolRunner)
    private val acpAgent = AcpAgent(moatbot)

    @Test
    fun `initialize returns agent info`() = runTest {
        val clientInfo = mock<ClientInfo> {
            on { protocolVersion } doReturn 1
        }

        val agentInfo = acpAgent.initialize(clientInfo)

        assertNotNull(agentInfo)
        assertEquals(false, agentInfo.capabilities.loadSession)
    }

    @Test
    fun `createSession returns new session`() = runTest {
        val params = SessionCreationParameters(cwd = "/test", mcpServers = emptyList())

        val session = acpAgent.createSession(params)

        assertNotNull(session)
        assertTrue(session.sessionId.value.startsWith("session-"))
    }

    @Test
    fun `session prompt returns text chunks`() = runTest {
        whenever(conversationRepository.findByKey(any())).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Text("Hello "))
            emit(AiEvent.Text("World!"))
            emit(AiEvent.Done)
        })

        val params = SessionCreationParameters(cwd = "/test", mcpServers = emptyList())
        val session = acpAgent.createSession(params)

        val content = listOf(ContentBlock.Text("Hi"))
        val events = session.prompt(content).toList()

        assertEquals(3, events.size)

        // First two should be text chunks
        assertIs<Event.SessionUpdateEvent>(events[0])
        val update0 = (events[0] as Event.SessionUpdateEvent).update
        assertIs<SessionUpdate.AgentMessageChunk>(update0)
        assertEquals("Hello ", (update0.content as ContentBlock.Text).text)

        assertIs<Event.SessionUpdateEvent>(events[1])
        val update1 = (events[1] as Event.SessionUpdateEvent).update
        assertIs<SessionUpdate.AgentMessageChunk>(update1)
        assertEquals("World!", (update1.content as ContentBlock.Text).text)

        // Last should be prompt response
        assertIs<Event.PromptResponseEvent>(events[2])
        assertEquals(StopReason.END_TURN, (events[2] as Event.PromptResponseEvent).response.stopReason)
    }

    @Test
    fun `session prompt handles empty content`() = runTest {
        val params = SessionCreationParameters(cwd = "/test", mcpServers = emptyList())
        val session = acpAgent.createSession(params)

        val content = emptyList<ContentBlock>()
        val events = session.prompt(content).toList()

        assertEquals(1, events.size)
        assertIs<Event.PromptResponseEvent>(events[0])
        assertEquals(StopReason.END_TURN, (events[0] as Event.PromptResponseEvent).response.stopReason)
    }

    @Test
    fun `session prompt handles errors`() = runTest {
        whenever(conversationRepository.findByKey(any())).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.Error("Something went wrong"))
        })

        val params = SessionCreationParameters(cwd = "/test", mcpServers = emptyList())
        val session = acpAgent.createSession(params)

        val content = listOf(ContentBlock.Text("Hi"))
        val events = session.prompt(content).toList()

        assertEquals(1, events.size)
        assertIs<Event.PromptResponseEvent>(events[0])
        assertEquals(StopReason.REFUSAL, (events[0] as Event.PromptResponseEvent).response.stopReason)
    }

    @Test
    fun `session prompt handles tool calls`() = runTest {
        val toolCall = ToolCall.create("test_tool", mapOf("arg" to "value"))

        whenever(conversationRepository.findByKey(any())).thenReturn(null)
        whenever(aiProvider.complete(any(), anyOrNull())).thenReturn(flow {
            emit(AiEvent.ToolCall(toolCall))
            emit(AiEvent.Text("Done"))
            emit(AiEvent.Done)
        })
        whenever(toolRunner.run(any())).thenReturn(ToolResult("tool output"))

        val params = SessionCreationParameters(cwd = "/test", mcpServers = emptyList())
        val session = acpAgent.createSession(params)

        val content = listOf(ContentBlock.Text("Use tool"))
        val events = session.prompt(content).toList()

        // Should have: tool started, tool completed, text chunk, prompt response
        assertEquals(4, events.size)

        assertIs<Event.SessionUpdateEvent>(events[0])
        val toolStarted = (events[0] as Event.SessionUpdateEvent).update
        assertIs<SessionUpdate.ToolCallUpdate>(toolStarted)

        assertIs<Event.SessionUpdateEvent>(events[1])
        val toolCompleted = (events[1] as Event.SessionUpdateEvent).update
        assertIs<SessionUpdate.ToolCallUpdate>(toolCompleted)
    }
}
