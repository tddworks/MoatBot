# Moatbot Architecture Plan (v5 - Final)

## Philosophy

- **Simple & Clear** - Easy to understand at a glance
- **3 Layers** - domain, app, infrastructure
- **Descriptive Names** - Interfaces describe what they do
- **User Mental Model** - "Send message → Get streaming response"

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                      INFRASTRUCTURE                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ Discord      │  │ Telegram     │  │ AcpAgent     │              │
│  │ Gateway      │  │ Gateway      │  │ (IDE)        │              │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘              │
│         │                 │                 │                       │
│         └────────────────┬┴─────────────────┘                       │
│                          │                                          │
│                          ▼                                          │
├─────────────────────────────────────────────────────────────────────┤
│                         APP                                         │
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  Moatbot                                                     │   │
│  │  └─ chat(key, message): Flow<ResponseEvent>                 │   │
│  │      - Gets/creates conversation                             │   │
│  │      - Adds message, calls AI, handles tools                │   │
│  │      - Streams response events                               │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                          │                                          │
│                          │ uses                                     │
│                          ▼                                          │
├─────────────────────────────────────────────────────────────────────┤
│                       DOMAIN                                        │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │  Models                                                        │ │
│  │  ├─ Conversation (aggregate) - state & behavior               │ │
│  │  ├─ Turn - current turn state                                 │ │
│  │  ├─ Message - user/assistant messages                         │ │
│  │  └─ ResponseEvent - streaming output                          │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
│  ┌───────────────────────────────────────────────────────────────┐ │
│  │  Interfaces (what domain needs from outside)                  │ │
│  │  ├─ AiProvider: provide AI completions                        │ │
│  │  ├─ ConversationRepository: store conversations               │ │
│  │  └─ ToolRunner: run tools                                     │ │
│  └───────────────────────────────────────────────────────────────┘ │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│                      INFRASTRUCTURE                                 │
│                                                                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ ClaudeCli    │  │ FileConv     │  │ LocalTool    │              │
│  │ :AiProvider  │  │ Repository   │  │ Runner       │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Domain Layer

### Models

```kotlin
// domain/Conversation.kt
class Conversation(
    val id: ConversationId,
    val key: SessionKey,
    val messages: List<Message>,
    val turn: Turn?,
    val aiSessionId: String?
) {
    companion object {
        fun start(key: SessionKey): Conversation
    }

    fun receive(userId: UserId, content: String): Conversation
    fun addTextChunk(chunk: String): Conversation
    fun addToolCall(call: ToolCall): Conversation
    fun addToolResult(callId: ToolCallId, result: String): Conversation
    fun complete(finalText: String): Conversation
    fun fail(error: String): Conversation

    fun hasPendingTools(): Boolean
    fun pendingTools(): List<ToolCall>
}

// domain/ResponseEvent.kt
sealed class ResponseEvent {
    data class TextChunk(val text: String) : ResponseEvent()
    data class ToolStarted(val call: ToolCall) : ResponseEvent()
    data class ToolCompleted(val callId: ToolCallId, val result: String) : ResponseEvent()
    data class Completed(val response: String) : ResponseEvent()
    data class Failed(val error: String) : ResponseEvent()
}
```

### Interfaces

```kotlin
// domain/AiProvider.kt
interface AiProvider {
    fun complete(messages: List<Message>, sessionId: String?): Flow<AiEvent>
}

sealed class AiEvent {
    data class Text(val text: String) : AiEvent()
    data class ToolCall(val call: com.moatbot.domain.ToolCall) : AiEvent()
    data class SessionId(val id: String) : AiEvent()
    object Done : AiEvent()
    data class Error(val message: String) : AiEvent()
}

// domain/ConversationRepository.kt
interface ConversationRepository {
    suspend fun findByKey(key: SessionKey): Conversation?
    suspend fun save(conversation: Conversation)
    suspend fun delete(key: SessionKey)
}

// domain/ToolRunner.kt
interface ToolRunner {
    suspend fun run(call: ToolCall): ToolResult
    fun availableTools(): List<ToolDefinition>
}

data class ToolResult(val output: String, val isError: Boolean = false)
```

---

## App Layer

### Moatbot

```kotlin
// app/Moatbot.kt
class Moatbot(
    private val aiProvider: AiProvider,
    private val conversationRepository: ConversationRepository,
    private val toolRunner: ToolRunner
) {
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
                    conv = conv.copy(aiSessionId = event.id)
                }
                is AiEvent.Done -> {
                    conv = conv.complete(conv.turn!!.text)
                    conversationRepository.save(conv)
                    emit(ResponseEvent.Completed(conv.turn!!.text))
                }
                is AiEvent.Error -> {
                    conv = conv.fail(event.message)
                    conversationRepository.save(conv)
                    emit(ResponseEvent.Failed(event.message))
                }
            }
        }
    }

    suspend fun clear(key: SessionKey) {
        conversationRepository.delete(key)
    }
}
```

---

## Infrastructure Layer

### Inbound (Gateways)

```kotlin
// infrastructure/DiscordGateway.kt
class DiscordGateway(
    private val moatbot: Moatbot,
    private val client: DiscordClient
) {
    suspend fun onMessage(userId: UserId, chatId: ChatId, content: String) {
        val key = SessionKey.from(userId, chatId)
        val response = StringBuilder()

        client.sendTypingIndicator(chatId)

        moatbot.chat(key, userId, content).collect { event ->
            when (event) {
                is ResponseEvent.TextChunk -> response.append(event.text)
                is ResponseEvent.Completed -> client.sendMessage(chatId, response.toString())
                is ResponseEvent.Failed -> client.sendMessage(chatId, "Error: ${event.error}")
                else -> { }
            }
        }
    }
}

// infrastructure/TelegramGateway.kt
class TelegramGateway(
    private val moatbot: Moatbot,
    private val client: TelegramClient
) {
    // Same pattern as Discord
}

// infrastructure/AcpAgent.kt
class AcpAgent(private val moatbot: Moatbot) : AgentSupport {

    override suspend fun createSession(params: SessionParams): AgentSession {
        return AcpSession(moatbot, SessionKey(params.sessionId ?: generateId()))
    }
}

class AcpSession(
    private val moatbot: Moatbot,
    private val key: SessionKey
) : AgentSession {

    override fun prompt(content: List<ContentBlock>): Flow<Event> = flow {
        val text = content.filterIsInstance<ContentBlock.Text>().joinToString(" ") { it.text }

        moatbot.chat(key, UserId("acp"), text).collect { event ->
            emit(event.toAcpEvent())
        }
    }
}

private fun ResponseEvent.toAcpEvent(): Event = when (this) {
    is ResponseEvent.TextChunk -> Event.SessionUpdate(
        SessionUpdate.AgentMessageChunk(ContentBlock.Text(text))
    )
    is ResponseEvent.ToolStarted -> Event.SessionUpdate(
        SessionUpdate.ToolCall(toolCallId = call.id, title = call.name, status = PENDING)
    )
    is ResponseEvent.ToolCompleted -> Event.SessionUpdate(
        SessionUpdate.ToolCallUpdate(toolCallId = callId, status = COMPLETED)
    )
    is ResponseEvent.Completed -> Event.PromptResponse(PromptResponse(StopReason.END_TURN))
    is ResponseEvent.Failed -> Event.PromptResponse(PromptResponse(StopReason.REFUSAL))
}
```

### Outbound (Implementations)

```kotlin
// infrastructure/ClaudeCli.kt
class ClaudeCli(
    private val cliPath: String,
    private val workingDir: String
) : AiProvider {

    override fun complete(messages: List<Message>, sessionId: String?): Flow<AiEvent> = flow {
        val prompt = messages.lastUserContent() ?: return@flow
        val result = execute(prompt, sessionId)

        if (result.success) {
            emit(AiEvent.Text(result.output))
            result.sessionId?.let { emit(AiEvent.SessionId(it)) }
            emit(AiEvent.Done)
        } else {
            emit(AiEvent.Error(result.output))
        }
    }
}

// infrastructure/FileConversationRepository.kt
class FileConversationRepository(private val dir: String) : ConversationRepository {
    override suspend fun findByKey(key: SessionKey): Conversation? = ...
    override suspend fun save(conversation: Conversation) = ...
    override suspend fun delete(key: SessionKey) = ...
}

// infrastructure/MemoryConversationRepository.kt
class MemoryConversationRepository : ConversationRepository {
    private val store = ConcurrentHashMap<SessionKey, Conversation>()
    override suspend fun findByKey(key: SessionKey) = store[key]
    override suspend fun save(conversation: Conversation) { store[conversation.key] = conversation }
    override suspend fun delete(key: SessionKey) { store.remove(key) }
}

// infrastructure/LocalToolRunner.kt
class LocalToolRunner : ToolRunner {
    private val tools = mutableMapOf<String, Tool>()

    override suspend fun run(call: ToolCall): ToolResult {
        val tool = tools[call.name] ?: return ToolResult("Unknown tool: ${call.name}", isError = true)
        return tool.execute(call.arguments)
    }

    override fun availableTools(): List<ToolDefinition> = tools.values.map { it.definition }
}
```

---

## Package Structure

```
src/main/kotlin/com/moatbot/
├── domain/
│   ├── Conversation.kt          # Aggregate
│   ├── Turn.kt                  # Turn state
│   ├── Message.kt               # Message types
│   ├── ToolCall.kt              # Tool call value object
│   ├── ResponseEvent.kt         # Streaming output events
│   ├── ValueObjects.kt          # IDs (ConversationId, SessionKey, etc.)
│   ├── AiProvider.kt            # Interface
│   ├── ConversationRepository.kt# Interface
│   └── ToolRunner.kt            # Interface
│
├── app/
│   └── Moatbot.kt               # Main orchestrator
│
├── infrastructure/
│   ├── DiscordGateway.kt        # Inbound
│   ├── TelegramGateway.kt       # Inbound
│   ├── AcpAgent.kt              # Inbound (ACP)
│   ├── ClaudeCli.kt             # Outbound: AiProvider
│   ├── FileConversationRepository.kt   # Outbound
│   ├── MemoryConversationRepository.kt # Outbound
│   └── LocalToolRunner.kt       # Outbound
│
└── Main.kt
```

---

## Data Flow

```
1. User sends message via Discord/Telegram/IDE
        │
        ▼
2. Gateway/AcpAgent calls moatbot.chat(key, userId, message)
        │
        ▼
3. Moatbot orchestrates:
   ├─ conversationRepository.findByKey(key)
   ├─ conversation.receive(message)
   ├─ aiProvider.complete(messages)
   ├─ toolRunner.run(call) if needed
   └─ conversationRepository.save(conversation)
        │
        ▼
4. Returns Flow<ResponseEvent>
        │
        ▼
5. Gateway handles events:
   - Discord/Telegram: collect text → send message
   - ACP: map to ACP events → stream to IDE
```

---

## Implementation Phases

### Phase 1: Domain
- [ ] `Conversation` aggregate
- [ ] `Turn`, `Message`, `ToolCall`, `ValueObjects`
- [ ] `ResponseEvent` sealed class
- [ ] Interfaces: `AiProvider`, `ConversationRepository`, `ToolRunner`

### Phase 2: App
- [ ] `Moatbot` with `chat()` method

### Phase 3: Infrastructure - Outbound
- [ ] `ClaudeCli` implementing `AiProvider`
- [ ] `FileConversationRepository`
- [ ] `MemoryConversationRepository`
- [ ] `LocalToolRunner`

### Phase 4: Infrastructure - Inbound
- [ ] `DiscordGateway`
- [ ] `TelegramGateway`
- [ ] Wire with existing messaging clients

### Phase 5: ACP Integration
- [ ] Add ACP SDK dependency
- [ ] `AcpAgent` implementing `AgentSupport`
- [ ] Wire STDIO transport

---

## Naming Summary

| Interface | Purpose | Implementations |
|-----------|---------|-----------------|
| `AiProvider` | Provide AI completions | `ClaudeCli` |
| `ConversationRepository` | Store/retrieve conversations | `FileConversationRepository`, `MemoryConversationRepository` |
| `ToolRunner` | Run tool calls | `LocalToolRunner` |

---

## Testing Strategy

1. **Domain Tests**: `Conversation` state transitions
2. **App Tests**: `Moatbot.chat()` with mock interfaces
3. **E2E Tests**: Full flow from gateway to response

---

## Summary

| Layer | Contents |
|-------|----------|
| **domain** | Conversation, ResponseEvent, AiProvider, ConversationRepository, ToolRunner |
| **app** | Moatbot |
| **infrastructure** | DiscordGateway, TelegramGateway, AcpAgent, ClaudeCli, FileConversationRepository, LocalToolRunner |

**One entry point**: `moatbot.chat(key, userId, message): Flow<ResponseEvent>`
