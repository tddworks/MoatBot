package com.moatbot.infrastructure

import com.moatbot.app.Moatbot
import com.moatbot.domain.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.*
import kotlin.test.Test

class TelegramGatewayTest {

    private val moatbot = mock<Moatbot>()
    private val client = mock<MessageClient>()
    private val commandHandler = mock<TelegramCommandHandler>()

    private val userId = UserId("user-123")
    private val chatId = ChatId("chat-456")

    private fun createIncomingMessage(content: String) = IncomingMessage(
        userId = userId,
        chatId = chatId,
        content = content,
        platform = Platform.TELEGRAM
    )

    @Test
    fun `routes command to command handler`() = runTest {
        // given
        val message = createIncomingMessage("/help")
        whenever(client.messages()).thenReturn(flowOf(message))
        whenever(commandHandler.isCommand("/help")).thenReturn(true)
        whenever(commandHandler.handleCommand("/help", userId, chatId)).thenReturn("Help message")

        val gateway = TelegramGateway(moatbot, client, commandHandler)

        // when
        gateway.start()

        // then
        verify(commandHandler).isCommand("/help")
        verify(commandHandler).handleCommand("/help", userId, chatId)
        verify(client).sendMessage(chatId, "Help message")
        verify(moatbot, never()).chat(any(), any(), any())
    }

    @Test
    fun `routes non-command to moatbot`() = runTest {
        // given
        val message = createIncomingMessage("hello")
        whenever(client.messages()).thenReturn(flowOf(message))
        whenever(commandHandler.isCommand("hello")).thenReturn(false)
        whenever(moatbot.chat(any(), any(), any())).thenReturn(flow {
            emit(ResponseEvent.TextChunk("Hi there!"))
            emit(ResponseEvent.Completed("Hi there!"))
        })

        val gateway = TelegramGateway(moatbot, client, commandHandler)

        // when
        gateway.start()

        // then
        verify(commandHandler).isCommand("hello")
        verify(moatbot).chat(SessionKey.from(userId, chatId), userId, "hello")
        verify(client).sendMessage(chatId, "Hi there!")
    }

    @Test
    fun `accumulates text chunks before sending`() = runTest {
        // given
        val message = createIncomingMessage("hi")
        whenever(client.messages()).thenReturn(flowOf(message))
        whenever(commandHandler.isCommand("hi")).thenReturn(false)
        whenever(moatbot.chat(any(), any(), any())).thenReturn(flow {
            emit(ResponseEvent.TextChunk("Hello "))
            emit(ResponseEvent.TextChunk("World!"))
            emit(ResponseEvent.Completed("Hello World!"))
        })

        val gateway = TelegramGateway(moatbot, client, commandHandler)

        // when
        gateway.start()

        // then
        verify(client).sendMessage(chatId, "Hello World!")
    }

    @Test
    fun `shows typing indicator while processing`() = runTest {
        // given
        val message = createIncomingMessage("hi")
        whenever(client.messages()).thenReturn(flowOf(message))
        whenever(commandHandler.isCommand("hi")).thenReturn(false)
        whenever(moatbot.chat(any(), any(), any())).thenReturn(flow {
            emit(ResponseEvent.TextChunk("Response"))
            emit(ResponseEvent.Completed("Response"))
        })

        val gateway = TelegramGateway(moatbot, client, commandHandler)

        // when
        gateway.start()

        // then
        verify(client).sendTypingIndicator(chatId)
        verify(client).stopTypingIndicator(chatId)
    }

    @Test
    fun `sends error message on AI failure`() = runTest {
        // given
        val message = createIncomingMessage("hi")
        whenever(client.messages()).thenReturn(flowOf(message))
        whenever(commandHandler.isCommand("hi")).thenReturn(false)
        whenever(moatbot.chat(any(), any(), any())).thenReturn(flow {
            emit(ResponseEvent.Failed("AI error"))
        })

        val gateway = TelegramGateway(moatbot, client, commandHandler)

        // when
        gateway.start()

        // then
        verify(client).sendMessage(eq(chatId), argThat<String> { contains("AI error") })
        verify(client).stopTypingIndicator(chatId)
    }

    @Test
    fun `trims message content`() = runTest {
        // given
        val message = createIncomingMessage("  hello  ")
        whenever(client.messages()).thenReturn(flowOf(message))
        whenever(commandHandler.isCommand("hello")).thenReturn(false)
        whenever(moatbot.chat(any(), any(), any())).thenReturn(flow {
            emit(ResponseEvent.Completed("Done"))
        })

        val gateway = TelegramGateway(moatbot, client, commandHandler)

        // when
        gateway.start()

        // then
        verify(commandHandler).isCommand("hello")
        verify(moatbot).chat(any(), any(), eq("hello"))
    }
}
