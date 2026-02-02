package com.moatbot.infrastructure

import com.moatbot.app.Moatbot
import com.moatbot.application.port.MessageClient
import com.moatbot.domain.ResponseEvent
import com.moatbot.domain.SessionKey
import com.moatbot.domain.UserId
import org.slf4j.LoggerFactory

/**
 * Discord gateway that connects the Discord client to Moatbot.
 */
class DiscordGateway(
    private val moatbot: Moatbot,
    private val client: MessageClient
) {
    private val logger = LoggerFactory.getLogger(DiscordGateway::class.java)

    // Command handlers
    private val commands = mapOf<String, suspend (SessionKey, String) -> String>(
        "/clear" to ::handleClearCommand,
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

    private suspend fun handleHelpCommand(key: SessionKey, args: String): String {
        return """
            **MoatBot Commands:**
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
