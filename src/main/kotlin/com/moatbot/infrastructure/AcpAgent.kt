package com.moatbot.infrastructure

import com.agentclientprotocol.agent.AgentInfo
import com.agentclientprotocol.agent.AgentSession
import com.agentclientprotocol.agent.AgentSupport
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.*
import com.moatbot.app.Moatbot
import com.moatbot.domain.ResponseEvent
import com.moatbot.domain.SessionKey
import com.moatbot.domain.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * ACP Agent implementation that wraps Moatbot.
 * Enables IDE integration via the Agent Client Protocol.
 */
class AcpAgent(
    private val moatbot: Moatbot
) : AgentSupport {

    private val logger = LoggerFactory.getLogger(AcpAgent::class.java)

    override suspend fun initialize(clientInfo: ClientInfo): AgentInfo {
        logger.info("Initializing ACP agent with protocol version ${clientInfo.protocolVersion}")

        return AgentInfo(
            protocolVersion = LATEST_PROTOCOL_VERSION,
            capabilities = AgentCapabilities(
                loadSession = false,
                promptCapabilities = PromptCapabilities(
                    audio = false,
                    image = false,
                    embeddedContext = true
                )
            ),
            authMethods = emptyList()
        )
    }

    override suspend fun createSession(sessionParameters: SessionCreationParameters): AgentSession {
        val sessionId = SessionId("session-${UUID.randomUUID()}")
        logger.info("Creating ACP session: $sessionId")
        return AcpSession(moatbot, sessionId)
    }
}

/**
 * ACP Session that handles prompts via Moatbot.
 */
class AcpSession(
    private val moatbot: Moatbot,
    override val sessionId: SessionId
) : AgentSession {

    private val logger = LoggerFactory.getLogger(AcpSession::class.java)
    private val key = SessionKey(sessionId.value)

    override suspend fun prompt(
        content: List<ContentBlock>,
        _meta: JsonElement?
    ): Flow<Event> = flow {
        logger.info("Processing prompt for session $sessionId")

        // Extract text from content blocks
        val text = content.filterIsInstance<ContentBlock.Text>()
            .joinToString(" ") { it.text }

        if (text.isBlank()) {
            emit(Event.PromptResponseEvent(PromptResponse(StopReason.END_TURN)))
            return@flow
        }

        // Process through Moatbot
        moatbot.chat(key, UserId("acp"), text).collect { event ->
            emit(event.toAcpEvent())
        }
    }

    override suspend fun cancel() {
        logger.info("Cancellation requested for session: $sessionId")
    }
}

/**
 * Convert Moatbot ResponseEvent to ACP Event.
 */
private fun ResponseEvent.toAcpEvent(): Event = when (this) {
    is ResponseEvent.TextChunk -> Event.SessionUpdateEvent(
        SessionUpdate.AgentMessageChunk(ContentBlock.Text(text))
    )

    is ResponseEvent.ToolStarted -> Event.SessionUpdateEvent(
        SessionUpdate.ToolCallUpdate(
            toolCallId = ToolCallId(call.id.value),
            title = call.name,
            status = ToolCallStatus.PENDING
        )
    )

    is ResponseEvent.ToolCompleted -> Event.SessionUpdateEvent(
        SessionUpdate.ToolCallUpdate(
            toolCallId = ToolCallId(callId.value),
            status = ToolCallStatus.COMPLETED,
            content = listOf(
                ToolCallContent.Content(ContentBlock.Text(result))
            )
        )
    )

    is ResponseEvent.Completed -> Event.PromptResponseEvent(
        PromptResponse(StopReason.END_TURN)
    )

    is ResponseEvent.Failed -> Event.PromptResponseEvent(
        PromptResponse(StopReason.REFUSAL)
    )
}
