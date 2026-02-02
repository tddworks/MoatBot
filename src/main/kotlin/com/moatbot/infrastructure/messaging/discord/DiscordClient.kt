package com.moatbot.infrastructure.messaging.discord

import com.moatbot.domain.IncomingMessage
import com.moatbot.domain.MessageClient
import com.moatbot.domain.Platform
import com.moatbot.domain.ChatId
import com.moatbot.domain.UserId
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class DiscordClient(
    private val token: String,
    private val debounceMs: Long = 2000  // Batch messages within 2 seconds
) : MessageClient {

    private val logger = LoggerFactory.getLogger(DiscordClient::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var kord: Kord? = null
    private var loginJob: Job? = null

    private val _messages = MutableSharedFlow<IncomingMessage>()

    // Debouncing: key = "userId:chatId", value = pending messages
    private val pendingMessages = ConcurrentHashMap<String, MutableList<String>>()
    private val debounceJobs = ConcurrentHashMap<String, Job>()
    private val debounceMutex = Mutex()

    // Active typing indicator jobs
    private val typingJobs = ConcurrentHashMap<String, Job>()

    override fun messages(): Flow<IncomingMessage> = _messages.asSharedFlow()

    override fun supportsEditableMessages(): Boolean = true

    override suspend fun sendMessage(chatId: ChatId, content: String) {
        // Stop typing indicator when sending message
        stopTypingIndicator(chatId)

        val channelId = Snowflake(chatId.value)

        // Split long messages into chunks (Discord limit is 2000 chars)
        val chunks = splitMessage(content, 1900)  // Leave room for formatting

        for (chunk in chunks) {
            kord?.rest?.channel?.createMessage(channelId) {
                this.content = chunk
            }
        }
    }

    /**
     * Send a placeholder message and return its ID for later editing.
     */
    override suspend fun sendPlaceholderMessage(chatId: ChatId, placeholder: String): String? {
        val channelId = Snowflake(chatId.value)
        return try {
            val message = kord?.rest?.channel?.createMessage(channelId) {
                this.content = placeholder
            }
            val msgId = message?.id?.toString()
            logger.debug("Created placeholder message: $msgId")
            msgId
        } catch (e: Exception) {
            logger.error("Failed to send placeholder message", e)
            null
        }
    }

    /**
     * Edit an existing message with new content.
     * Falls back to sending a new message if edit fails.
     */
    override suspend fun editMessage(chatId: ChatId, messageId: String, content: String) {
        val channelId = Snowflake(chatId.value)
        val msgId = Snowflake(messageId)
        // For edit, just take first 2000 chars (can't split an edit)
        val truncated = if (content.length > 2000) content.take(1997) + "..." else content

        logger.debug("Editing message $messageId with ${truncated.length} chars")

        try {
            kord?.rest?.channel?.editMessage(channelId, msgId) {
                this.content = truncated
            }
            logger.debug("Successfully edited message $messageId")
        } catch (e: Exception) {
            logger.error("Failed to edit message $messageId, sending new message instead", e)
            // Fallback: send as new message (can split)
            sendMessage(chatId, content)
        }
    }

    /**
     * Start a continuous typing indicator that refreshes every 8 seconds.
     * Discord typing indicator lasts ~10 seconds, so we refresh at 8s.
     */
    override suspend fun sendTypingIndicator(chatId: ChatId) {
        startTypingIndicator(chatId)
    }

    /**
     * Start continuous typing indicator for a channel.
     */
    fun startTypingIndicator(chatId: ChatId) {
        val key = chatId.value

        // Cancel existing typing job if any
        typingJobs[key]?.cancel()

        // Start new continuous typing job
        typingJobs[key] = scope.launch {
            while (isActive) {
                try {
                    kord?.rest?.channel?.triggerTypingIndicator(Snowflake(chatId.value))
                    delay(8000)  // Refresh every 8 seconds (Discord typing lasts ~10s)
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    logger.debug("Typing indicator failed: ${e.message}")
                    delay(2000)  // Retry after short delay
                }
            }
        }
    }

    /**
     * Stop the continuous typing indicator for a channel.
     */
    override suspend fun stopTypingIndicator(chatId: ChatId) {
        val key = chatId.value
        typingJobs[key]?.cancel()
        typingJobs.remove(key)
    }

    @OptIn(PrivilegedIntent::class)
    override suspend fun start() {
        logger.info("Starting Discord bot...")

        kord = Kord(token)

        kord?.on<MessageCreateEvent> {
            // Ignore bot messages
            if (message.author?.isBot == true) return@on

            val rawContent = message.content
            if (rawContent.isBlank()) return@on

            val author = message.author ?: return@on

            // Strip Discord mentions (user, role, channel) from the message
            val content = stripDiscordMentions(rawContent)
            if (content.isBlank()) return@on  // Ignore messages that are only mentions

            val userId = UserId(author.id.toString())
            val chatId = ChatId(message.channelId.toString())

            logger.debug("Received message from ${author.username}: ${content.take(50)}...")

            // Debounce messages from the same user in the same channel
            debounceMessage(userId, chatId, content)
        }

        loginJob = scope.launch {
            while (isActive) {
                try {
                    kord?.login {
                        intents += Intent.GuildMessages
                        intents += Intent.DirectMessages
                        intents += Intent.MessageContent
                    }
                } catch (e: Exception) {
                    logger.warn("Discord connection lost, reconnecting in 5s...", e)
                    delay(5000)
                }
            }
        }

        logger.info("Discord bot started")
    }

    private suspend fun debounceMessage(userId: UserId, chatId: ChatId, content: String) {
        val key = "${userId.value}:${chatId.value}"

        debounceMutex.withLock {
            // Add message to pending list
            val messages = pendingMessages.getOrPut(key) { mutableListOf() }
            messages.add(content)

            // Cancel existing debounce job for this key
            debounceJobs[key]?.cancel()

            // Start new debounce job
            debounceJobs[key] = scope.launch {
                delay(debounceMs)

                // Flush pending messages
                val batch = debounceMutex.withLock {
                    pendingMessages.remove(key) ?: emptyList()
                }

                if (batch.isNotEmpty()) {
                    // Combine all messages with newlines
                    val combinedContent = batch.joinToString("\n")

                    val incoming = IncomingMessage(
                        userId = userId,
                        chatId = chatId,
                        content = combinedContent,
                        platform = Platform.DISCORD
                    )

                    logger.debug("Emitting debounced message (${batch.size} batched): ${combinedContent.take(50)}...")
                    _messages.emit(incoming)
                }

                debounceJobs.remove(key)
            }
        }
    }

    override suspend fun stop() {
        logger.info("Stopping Discord bot...")

        // Cancel all typing jobs
        typingJobs.values.forEach { it.cancel() }
        typingJobs.clear()

        // Cancel all debounce jobs
        debounceJobs.values.forEach { it.cancel() }
        debounceJobs.clear()
        pendingMessages.clear()

        kord?.shutdown()
        loginJob?.cancel()
        scope.cancel()
    }

    /**
     * Strips Discord mentions from message content.
     * Removes: <@123> (user), <@!123> (nickname), <@&123> (role), <#123> (channel)
     */
    private fun stripDiscordMentions(content: String): String {
        return content
            .replace(Regex("<@!?\\d+>"), "")     // User mentions
            .replace(Regex("<@&\\d+>"), "")      // Role mentions
            .replace(Regex("<#\\d+>"), "")       // Channel mentions
            .trim()
    }

    /**
     * Split message into chunks that fit Discord's 2000 character limit.
     * Tries to split at newlines for cleaner output.
     */
    private fun splitMessage(content: String, maxLength: Int = 1900): List<String> {
        if (content.length <= maxLength) {
            return listOf(content)
        }

        val chunks = mutableListOf<String>()
        var remaining = content

        while (remaining.isNotEmpty()) {
            if (remaining.length <= maxLength) {
                chunks.add(remaining)
                break
            }

            // Try to find a good split point (newline) within the limit
            var splitIndex = remaining.lastIndexOf('\n', maxLength)

            // If no newline found, try space
            if (splitIndex <= 0) {
                splitIndex = remaining.lastIndexOf(' ', maxLength)
            }

            // If still no good split point, just cut at maxLength
            if (splitIndex <= 0) {
                splitIndex = maxLength
            }

            chunks.add(remaining.substring(0, splitIndex))
            remaining = remaining.substring(splitIndex).trimStart()
        }

        return chunks
    }
}
