package com.moatbot.app

import com.moatbot.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

/**
 * Main orchestrator for the Moatbot application.
 * Provides a simple interface for chat interactions.
 */
class Moatbot(
    private val aiProvider: AiProvider,
    private val conversationRepository: ConversationRepository,
    private val toolRunner: ToolRunner
) {
    private val logger = LoggerFactory.getLogger(Moatbot::class.java)

    /**
     * Main entry point: send a message, get streaming response.
     */
    fun chat(key: SessionKey, userId: UserId, message: String): Flow<ResponseEvent> = flow {
        // 1. Get or create conversation
        var conv = conversationRepository.findByKey(key) ?: Conversation.start(key)

        // 2. Receive user message
        conv = conv.receive(userId, message)
        conversationRepository.save(conv)

        // 3. Stream AI response
        try {
            aiProvider.complete(conv.messages, conv.aiSessionId).collect { event ->
                when (event) {
                    is AiEvent.Text -> {
                        conv = conv.addTextChunk(event.text)
                        emit(ResponseEvent.TextChunk(event.text))
                    }

                    is AiEvent.ToolCall -> {
                        conv = conv.addToolCall(event.call)
                        emit(ResponseEvent.ToolStarted(event.call))

                        // Run tool
                        val result = toolRunner.run(event.call)
                        conv = conv.addToolResult(event.call.id, result.output)
                        emit(ResponseEvent.ToolCompleted(event.call.id, result.output))
                    }

                    is AiEvent.SessionId -> {
                        conv = conv.withAiSessionId(event.id)
                    }

                    is AiEvent.Done -> {
                        val finalText = conv.turn?.text ?: ""
                        conv = conv.complete(finalText)
                        conversationRepository.save(conv)
                        emit(ResponseEvent.Completed(finalText))
                    }

                    is AiEvent.Error -> {
                        conv = conv.fail(event.message)
                        conversationRepository.save(conv)
                        emit(ResponseEvent.Failed(event.message))
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error during chat", e)
            conv = conv.fail(e.message ?: "Unknown error")
            conversationRepository.save(conv)
            emit(ResponseEvent.Failed(e.message ?: "Unknown error"))
        }
    }

    /**
     * Clear a conversation.
     */
    suspend fun clear(key: SessionKey) {
        conversationRepository.delete(key)
    }

    /**
     * Get conversation status.
     */
    suspend fun status(key: SessionKey): ConversationStatus? {
        val conv = conversationRepository.findByKey(key) ?: return null
        return ConversationStatus(
            messageCount = conv.messages.size,
            aiSessionId = conv.aiSessionId,
            createdAt = conv.createdAt
        )
    }
}

data class ConversationStatus(
    val messageCount: Int,
    val aiSessionId: String?,
    val createdAt: java.time.Instant
)
