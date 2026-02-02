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
 * Slash command handler for Discord that uses Moatbot.
 * Extracted to break circular dependency between DiscordClient and DiscordGateway.
 */
class DiscordSlashCommandHandler(private val moatbot: Moatbot) : SlashCommandHandler {
    private val logger = LoggerFactory.getLogger(DiscordSlashCommandHandler::class.java)

    override suspend fun handleCommand(command: String, userId: UserId, chatId: ChatId): String {
        val key = SessionKey.from(userId, chatId)
        return when (command.lowercase()) {
            "/new" -> {
                moatbot.clear(key)
                logger.info("New session started: $key")
                "✅ New session started"
            }
            "/clear" -> {
                moatbot.clear(key)
                logger.info("Session cleared: $key")
                "Session cleared. Starting fresh!"
            }
            "/help" -> """
                **MoatBot Commands:**
                `/new` - Start a new session
                `/clear` - Clear conversation history and start fresh
                `/status` - Show current session info
                `/help` - Show this help message

                Just type normally to chat with Claude!
            """.trimIndent()
            "/status" -> {
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
    private val client: MessageClient
) {
    private val logger = LoggerFactory.getLogger(DiscordGateway::class.java)

    // Command handlers for text-based commands (fallback)
    private val commands = mapOf<String, suspend (SessionKey, String) -> String>(
        "/clear" to ::handleClearCommand,
        "/new" to ::handleNewCommand,
        "/help" to ::handleHelpCommand,
        "/status" to ::handleStatusCommand
    )

    suspend fun start() {
        logger.info("DiscordGateway starting...")
        client.start()

        client.messages().collect { incoming ->
            try {
                val key = SessionKey.from(incoming.userId, incoming.chatId)
                val content = incoming.content.trim()

                // Check for commands first
                val command = commands.keys.firstOrNull { content.lowercase().startsWith(it) }
                if (command != null) {
                    val args = content.drop(command.length).trim()
                    val response = commands[command]!!.invoke(key, args)
                    client.sendMessage(incoming.chatId, response)
                    return@collect
                }

                // Show typing indicator while processing
                client.sendTypingIndicator(incoming.chatId)

                // Collect response from Moatbot
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

    private suspend fun handleClearCommand(key: SessionKey, args: String): String {
        moatbot.clear(key)
        logger.info("Session cleared: $key")
        return "Session cleared. Starting fresh!"
    }

    private suspend fun handleNewCommand(key: SessionKey, args: String): String {
        moatbot.clear(key)
        logger.info("New session started: $key")
        return "✅ New session started"
    }

    private suspend fun handleHelpCommand(key: SessionKey, args: String): String {
        return """
            **MoatBot Commands:**
            `/new` - Start a new session
            `/clear` - Clear conversation history and start fresh
            `/status` - Show current session info
            `/help` - Show this help message

            Just type normally to chat with Claude!
        """.trimIndent()
    }

    private suspend fun handleStatusCommand(key: SessionKey, args: String): String {
        val status = moatbot.status(key)
            ?: return "No active session. Send a message to start!"

        return """
            **Session Status:**
            - Messages: ${status.messageCount}
            - AI Session: ${status.aiSessionId?.take(20) ?: "none"}...
            - Created: ${status.createdAt}
        """.trimIndent()
    }
}
