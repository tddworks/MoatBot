package com.moatbot.infrastructure

import com.moatbot.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Instant

/**
 * File-based implementation of ConversationRepository.
 * Persists conversations as JSON files.
 */
class FileConversationRepository(
    private val directory: String
) : ConversationRepository {

    private val logger = LoggerFactory.getLogger(FileConversationRepository::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    init {
        File(directory).mkdirs()
    }

    override suspend fun findByKey(key: SessionKey): Conversation? = withContext(Dispatchers.IO) {
        val file = getFile(key)
        if (!file.exists()) {
            return@withContext null
        }

        try {
            val content = file.readText()
            val dto = json.decodeFromString<ConversationDto>(content)
            dto.toConversation()
        } catch (e: Exception) {
            logger.error("Failed to read conversation from ${file.absolutePath}", e)
            null
        }
    }

    override suspend fun save(conversation: Conversation): Unit = withContext(Dispatchers.IO) {
        val file = getFile(conversation.key)
        try {
            val dto = ConversationDto.fromConversation(conversation)
            val content = json.encodeToString(dto)
            file.writeText(content)
            logger.debug("Saved conversation to ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to save conversation to ${file.absolutePath}", e)
            throw e
        }
    }

    override suspend fun delete(key: SessionKey): Unit = withContext(Dispatchers.IO) {
        val file = getFile(key)
        if (file.exists()) {
            file.delete()
            logger.debug("Deleted conversation file ${file.absolutePath}")
        }
    }

    private fun getFile(key: SessionKey): File {
        val filename = key.value
            .replace(":", "_")
            .replace("/", "_")
            .replace("\\", "_")
            .replace("@", "_at_")
            .replace("#", "_hash_") + ".json"
        return File(directory, filename)
    }
}

@Serializable
private data class ConversationDto(
    val id: String,
    val key: String,
    val aiSessionId: String?,
    val messages: List<MessageDto>,
    val createdAt: String,
    val updatedAt: String
) {
    fun toConversation(): Conversation = Conversation(
        id = ConversationId(id),
        key = SessionKey(key),
        aiSessionId = aiSessionId,
        messages = messages.map { it.toMessage() },
        turn = null,
        createdAt = Instant.parse(createdAt),
        updatedAt = Instant.parse(updatedAt)
    )

    companion object {
        fun fromConversation(conversation: Conversation): ConversationDto = ConversationDto(
            id = conversation.id.value,
            key = conversation.key.value,
            aiSessionId = conversation.aiSessionId,
            messages = conversation.messages.map { MessageDto.fromMessage(it) },
            createdAt = conversation.createdAt.toString(),
            updatedAt = conversation.updatedAt.toString()
        )
    }
}

@Serializable
private data class MessageDto(
    val type: String,
    val id: String,
    val userId: String? = null,
    val content: String? = null,
    val toolCalls: List<ToolCallDto>? = null,
    val toolCallId: String? = null,
    val isError: Boolean? = null,
    val timestamp: String
) {
    fun toMessage(): Message = when (type) {
        "user" -> Message.User(
            id = MessageId(id),
            userId = UserId(userId!!),
            content = content!!,
            timestamp = Instant.parse(timestamp)
        )
        "assistant" -> Message.Assistant(
            id = MessageId(id),
            content = content!!,
            timestamp = Instant.parse(timestamp)
        )
        "assistant_tool_use" -> Message.AssistantToolUse(
            id = MessageId(id),
            toolCalls = toolCalls!!.map { it.toToolCall() },
            timestamp = Instant.parse(timestamp)
        )
        "tool_result" -> Message.ToolResult(
            id = MessageId(id),
            toolCallId = ToolCallId(toolCallId!!),
            content = content!!,
            isError = isError ?: false,
            timestamp = Instant.parse(timestamp)
        )
        else -> throw IllegalArgumentException("Unknown message type: $type")
    }

    companion object {
        fun fromMessage(message: Message): MessageDto = when (message) {
            is Message.User -> MessageDto(
                type = "user",
                id = message.id.value,
                userId = message.userId.value,
                content = message.content,
                timestamp = message.timestamp.toString()
            )
            is Message.Assistant -> MessageDto(
                type = "assistant",
                id = message.id.value,
                content = message.content,
                timestamp = message.timestamp.toString()
            )
            is Message.AssistantToolUse -> MessageDto(
                type = "assistant_tool_use",
                id = message.id.value,
                toolCalls = message.toolCalls.map { ToolCallDto.fromToolCall(it) },
                timestamp = message.timestamp.toString()
            )
            is Message.ToolResult -> MessageDto(
                type = "tool_result",
                id = message.id.value,
                toolCallId = message.toolCallId.value,
                content = message.content,
                isError = message.isError,
                timestamp = message.timestamp.toString()
            )
        }
    }
}

@Serializable
private data class ToolCallDto(
    val id: String,
    val name: String,
    val arguments: Map<String, String>
) {
    fun toToolCall(): ToolCall = ToolCall(
        id = ToolCallId(id),
        name = name,
        arguments = arguments
    )

    companion object {
        fun fromToolCall(toolCall: ToolCall): ToolCallDto = ToolCallDto(
            id = toolCall.id.value,
            name = toolCall.name,
            arguments = toolCall.arguments
        )
    }
}
