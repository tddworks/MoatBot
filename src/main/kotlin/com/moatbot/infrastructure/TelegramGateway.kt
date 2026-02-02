package com.moatbot.infrastructure

import com.moatbot.app.Moatbot
import com.moatbot.domain.ChatId
import com.moatbot.domain.MessageClient
import com.moatbot.domain.ResponseEvent
import com.moatbot.domain.SessionKey
import com.moatbot.domain.UserId
import org.slf4j.LoggerFactory

/**
 * Command handler for Telegram that uses Moatbot.
 * Extracted to make command handling testable.
 */
class TelegramCommandHandler(private val moatbot: Moatbot) {
    private val logger = LoggerFactory.getLogger(TelegramCommandHandler::class.java)

    private val commands = listOf("/clear", "/new", "/help", "/status", "/start")

    /**
     * Check if the content is a command.
     */
    fun isCommand(content: String): Boolean {
        val trimmed = content.trim().lowercase()
        return commands.any { trimmed.startsWith(it) }
    }

    /**
     * Handle a command and return the response.
     */
    suspend fun handleCommand(command: String, userId: UserId, chatId: ChatId): String {
        val key = SessionKey.from(userId, chatId)
        val trimmed = command.trim().lowercase()

        return when {
            trimmed.startsWith("/start") -> handleStartCommand()
            trimmed.startsWith("/new") -> handleNewCommand(key)
            trimmed.startsWith("/clear") -> handleClearCommand(key)
            trimmed.startsWith("/help") -> handleHelpCommand()
            trimmed.startsWith("/status") -> handleStatusCommand(key)
            else -> "Unknown command: $command"
        }
    }

    private fun handleStartCommand(): String = """
        Welcome to MoatBot!

        I'm an AI assistant powered by Claude. Just send me a message to chat!

        Commands:
        /new - Start a new session
        /clear - Clear conversation history
        /status - Show session info
        /help - Show help
    """.trimIndent()

    private suspend fun handleNewCommand(key: SessionKey): String {
        moatbot.clear(key)
        logger.info("New session started: $key")
        return "✅ New session started"
    }

    private suspend fun handleClearCommand(key: SessionKey): String {
        moatbot.clear(key)
        logger.info("Session cleared: $key")
        return "Session cleared. Starting fresh!"
    }

    private fun handleHelpCommand(): String = """
        MoatBot Commands:
        /new - Start a new session
        /clear - Clear conversation history and start fresh
        /status - Show current session info
        /help - Show this help message

        Just type normally to chat with Claude!
    """.trimIndent()

    private suspend fun handleStatusCommand(key: SessionKey): String {
        val status = moatbot.status(key)
            ?: return "No active session. Send a message to start!"

        return """
            Session Status:
            - Messages: ${status.messageCount}
            - AI Session: ${status.aiSessionId?.take(20) ?: "none"}...
            - Created: ${status.createdAt}
        """.trimIndent()
    }
}

/**
 * Telegram gateway that connects the Telegram client to Moatbot.
 */
class TelegramGateway(
    private val moatbot: Moatbot,
    private val client: MessageClient,
    private val commandHandler: TelegramCommandHandler = TelegramCommandHandler(moatbot)
) {
    private val logger = LoggerFactory.getLogger(TelegramGateway::class.java)

    suspend fun start() {
        logger.info("TelegramGateway starting...")
        client.start()

        client.messages().collect { incoming ->
            try {
                val content = incoming.content.trim()

                // Check for commands first
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
        logger.info("TelegramGateway stopping...")
        client.stop()
    }
}
