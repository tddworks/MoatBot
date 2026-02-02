package com.moatbot.infrastructure

import com.moatbot.app.Moatbot
import com.moatbot.domain.ChatId
import com.moatbot.domain.MessageClient
import com.moatbot.domain.ResponseEvent
import com.moatbot.domain.SessionKey
import com.moatbot.domain.UserId
import com.moatbot.infrastructure.messaging.discord.SlashCommandHandler
import org.slf4j.LoggerFactory

/**
 * Command handler for Discord that uses Moatbot.
 * Handles both slash commands and text-based commands.
 * Extracted to break circular dependency and improve testability.
 */
class DiscordSlashCommandHandler(private val moatbot: Moatbot) : SlashCommandHandler {
    private val logger = LoggerFactory.getLogger(DiscordSlashCommandHandler::class.java)

    private val commands = listOf("/new", "/clear", "/help", "/status")

    /**
     * Check if the content is a command.
     */
    fun isCommand(content: String): Boolean {
        val trimmed = content.trim().lowercase()
        return commands.any { trimmed.startsWith(it) }
    }

    override suspend fun handleCommand(command: String, userId: UserId, chatId: ChatId): String {
        val key = SessionKey.from(userId, chatId)
        val trimmed = command.trim().lowercase()

        return when {
            trimmed.startsWith("/new") -> {
                moatbot.clear(key)
                logger.info("New session started: $key")
                "✅ New session started"
            }
            trimmed.startsWith("/clear") -> {
                moatbot.clear(key)
                logger.info("Session cleared: $key")
                "Session cleared. Starting fresh!"
            }
            trimmed.startsWith("/help") -> """
                **MoatBot Commands:**
                `/new` - Start a new session
                `/clear` - Clear conversation history and start fresh
                `/status` - Show current session info
                `/help` - Show this help message

                Just type normally to chat with Claude!
            """.trimIndent()
            trimmed.startsWith("/status") -> {
                val status = moatbot.status(key)
                if (status == null) {
                    "No active session. Send a message to start!"
                } else {
                    """
                        **Session Status:**
                        - Messages: ${status.messageCount}
                        - AI Session: ${status.aiSessionId?.take(20) ?: "none"}...
                        - Created: ${status.createdAt}
                    """.trimIndent()
                }
            }
            else -> "Unknown command: $command"
        }
    }
}

/**
 * Discord gateway that connects the Discord client to Moatbot.
 */
class DiscordGateway(
    private val moatbot: Moatbot,
    private val client: MessageClient,
    private val commandHandler: DiscordSlashCommandHandler = DiscordSlashCommandHandler(moatbot)
) {
    private val logger = LoggerFactory.getLogger(DiscordGateway::class.java)

    suspend fun start() {
        logger.info("DiscordGateway starting...")
        client.start()

        client.messages().collect { incoming ->
            try {
                val content = incoming.content.trim()

                // Check for commands first (text-based fallback for slash commands)
                if (commandHandler.isCommand(content)) {
                    val response = commandHandler.handleCommand(content, incoming.userId, incoming.chatId)
                    client.sendMessage(incoming.chatId, response)
                    return@collect
                }

                // Show typing indicator while processing
                client.sendTypingIndicator(incoming.chatId)

                // Collect response from Moatbot
                val key = SessionKey.from(incoming.userId, incoming.chatId)
                val response = StringBuilder()

                moatbot.chat(key, incoming.userId, content).collect { event ->
                    when (event) {
                        is ResponseEvent.TextChunk -> response.append(event.text)
                        is ResponseEvent.Completed -> {
                            client.stopTypingIndicator(incoming.chatId)
                            client.sendMessage(incoming.chatId, response.toString())
                        }
                        is ResponseEvent.Failed -> {
                            client.stopTypingIndicator(incoming.chatId)
                            client.sendMessage(incoming.chatId, "Error: ${event.error}")
                        }
                        is ResponseEvent.ToolStarted -> {
                            logger.debug("Tool started: ${event.call.name}")
                        }
                        is ResponseEvent.ToolCompleted -> {
                            logger.debug("Tool completed: ${event.callId}")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Error handling message", e)
                client.stopTypingIndicator(incoming.chatId)
                client.sendMessage(incoming.chatId, "An error occurred: ${e.message}")
            }
        }
    }

    suspend fun stop() {
        logger.info("DiscordGateway stopping...")
        client.stop()
    }
}
