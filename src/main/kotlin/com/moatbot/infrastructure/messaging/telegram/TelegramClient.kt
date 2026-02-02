package com.moatbot.infrastructure.messaging.telegram

import com.moatbot.application.port.IncomingMessage
import com.moatbot.application.port.MessageClient
import com.moatbot.application.port.Platform
import com.moatbot.domain.ChatId
import com.moatbot.domain.UserId
import dev.inmo.tgbotapi.bot.ktor.telegramBot
import dev.inmo.tgbotapi.extensions.api.bot.getMe
import dev.inmo.tgbotapi.extensions.api.send.sendTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviourWithLongPolling
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onText
import dev.inmo.tgbotapi.types.toChatId
import dev.inmo.tgbotapi.utils.RiskFeature
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class TelegramClient(
    private val token: String
) : MessageClient {

    private val logger = LoggerFactory.getLogger(TelegramClient::class.java)
    private val bot = telegramBot(token)
    private val scope = CoroutineScope(Dispatchers.Default)
    private var pollingJob: Job? = null

    private val _messages = MutableSharedFlow<IncomingMessage>()

    override fun messages(): Flow<IncomingMessage> = _messages.asSharedFlow()

    override suspend fun sendMessage(chatId: ChatId, content: String) {
        try {
            bot.sendTextMessage(
                chatId = chatId.value.toLong().toChatId(),
                text = content
            )
        } catch (e: Exception) {
            logger.error("Failed to send message to chat ${chatId.value}", e)
            throw e
        }
    }

    override suspend fun sendTypingIndicator(chatId: ChatId) {
        // Telegram doesn't have a direct typing indicator API in tgbotapi
        // This is handled automatically by the library in some cases
    }

    @OptIn(RiskFeature::class)
    override suspend fun start() {
        logger.info("Starting Telegram bot...")

        val me = bot.getMe()
        logger.info("Bot started: @${me.username}")

        pollingJob = scope.launch {
            bot.buildBehaviourWithLongPolling {
                onText { message ->
                    val text = message.content.text
                    val chat = message.chat
                    val fromUser = message.content.textSources.firstOrNull()

                    // Use chat id as user id if from is not available
                    val userId = chat.id.chatId.toString()

                    val incoming = IncomingMessage(
                        userId = UserId(userId),
                        chatId = ChatId(chat.id.chatId.toString()),
                        content = text,
                        platform = Platform.TELEGRAM
                    )

                    logger.debug("Received message from $userId: ${text.take(50)}...")
                    _messages.emit(incoming)
                }
            }.join()
        }
    }

    override suspend fun stop() {
        logger.info("Stopping Telegram bot...")
        pollingJob?.cancel()
        scope.cancel()
    }
}
