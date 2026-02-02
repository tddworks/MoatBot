# MoatBot - Design Document

## Overview

MoatBot is a Kotlin application that connects Claude CLI to messaging platforms (Telegram, Discord) and IDEs (via ACP - Agent Client Protocol).

## Architecture

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│    Telegram     │  │     Discord     │  │    IDE (ACP)    │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│ TelegramGateway │  │ DiscordGateway  │  │    AcpAgent     │
└────────┬────────┘  └────────┬────────┘  └────────┬────────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              │
                              ▼
                    ┌───────────────────┐
                    │      Moatbot      │  ← Orchestrator
                    │  chat() → Flow    │
                    └─────────┬─────────┘
                              │
              ┌───────────────┼───────────────┐
              │               │               │
              ▼               ▼               ▼
     ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
     │  AiProvider │  │ Conversation│  │ ToolRunner  │
     │ (ClaudeCli) │  │  Repository │  │             │
     └─────────────┘  └─────────────┘  └─────────────┘
              │
              ▼
     ┌─────────────────────────┐
     │  claude --print -p ...  │
     └─────────────────────────┘
```

## Project Structure

```
src/main/kotlin/com/moatbot/
├── Main.kt                           # Entry point (--acp flag for IDE mode)
│
├── app/
│   └── Moatbot.kt                    # Main orchestrator
│
├── domain/                           # Pure domain model
│   ├── ValueObjects.kt               # UserId, ChatId, SessionKey, etc.
│   ├── Message.kt                    # User/Assistant messages
│   ├── Conversation.kt               # Aggregate root
│   ├── Turn.kt                       # In-progress response state
│   ├── ToolCall.kt                   # Tool call model
│   ├── ResponseEvent.kt              # Events for streaming responses
│   ├── AiProvider.kt                 # AI provider interface + AiEvent
│   ├── ConversationRepository.kt     # Repository interface
│   └── ToolRunner.kt                 # Tool runner interface
│
├── application/
│   └── port/
│       └── MessageClient.kt          # Messaging platform interface
│
└── infrastructure/
    ├── ClaudeCli.kt                  # Claude CLI subprocess wrapper
    ├── MemoryConversationRepository.kt
    ├── FileConversationRepository.kt
    ├── LocalToolRunner.kt
    ├── TelegramGateway.kt            # Telegram → Moatbot bridge
    ├── DiscordGateway.kt             # Discord → Moatbot bridge
    ├── AcpAgent.kt                   # ACP agent for IDE integration
    └── messaging/
        ├── telegram/TelegramClient.kt
        └── discord/DiscordClient.kt
```

## Key Domain Types

```kotlin
// Value Objects (inline value classes)
@JvmInline value class UserId(val value: String)
@JvmInline value class ChatId(val value: String)
@JvmInline value class SessionKey(val value: String)
@JvmInline value class ConversationId(val value: String)
@JvmInline value class ToolCallId(val value: String)
@JvmInline value class MessageId(val value: String)

// Messages
sealed class Message {
    data class User(...) : Message()
    data class Assistant(...) : Message()
}

// AI Provider events (for streaming from Claude CLI)
sealed class AiEvent {
    data class Text(val text: String) : AiEvent()
    data class ToolCall(val call: ToolCall) : AiEvent()
    data class SessionId(val id: String) : AiEvent()
    data object Done : AiEvent()
    data class Error(val message: String) : AiEvent()
}

// Response events (for streaming to clients)
sealed class ResponseEvent {
    data class TextChunk(val text: String) : ResponseEvent()
    data class ToolStarted(val call: ToolCall) : ResponseEvent()
    data class ToolCompleted(val callId: ToolCallId, val result: String) : ResponseEvent()
    data class Completed(val fullText: String) : ResponseEvent()
    data class Failed(val error: String) : ResponseEvent()
}
```

## Core Flow

### 1. Message Reception
Gateway receives message → creates `SessionKey` from userId:chatId

### 2. Moatbot.chat()
```kotlin
fun chat(key: SessionKey, userId: UserId, message: String): Flow<ResponseEvent>
```
- Gets or creates Conversation
- Adds user message, starts new Turn
- Streams AI response via AiProvider
- Handles tool calls through ToolRunner
- Emits ResponseEvent for each update

### 3. Conversation State Machine
```
receive(userId, content)  →  Adds User message, creates Turn
addTextChunk(chunk)       →  Appends to Turn.text
addToolCall(call)         →  Adds to Turn.pendingToolCalls
addToolResult(id, result) →  Moves call to completedToolCalls
complete(finalText)       →  Creates Assistant message, clears Turn
fail(error)               →  Clears Turn
```

### 4. Claude CLI Execution
```bash
claude --print --dangerously-skip-permissions -p "prompt"
claude --print --resume <session-id> -p "prompt"
```

## Dependencies

```kotlin
// build.gradle.kts
dependencies {
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    // Telegram (tgbotapi)
    implementation("dev.inmo:tgbotapi:30.0.2")

    // Discord (Kord)
    implementation("dev.kord:kord-core:0.17.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // ACP (Agent Client Protocol)
    implementation("com.agentclientprotocol:acp:0.15.2")

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.mockito:mockito-core:5.21.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.3")
}
```

## Configuration

```bash
# Environment variables
TELEGRAM_BOT_TOKEN=...              # Required for Telegram
DISCORD_BOT_TOKEN=...               # Required for Discord
SESSION_DIR=~/.moatbot/sessions     # Persistence (":memory:" for in-memory)
CLAUDE_CLI_PATH=claude              # Path to claude CLI
CLAUDE_WORKING_DIR=.                # Working directory for CLI
```

## Running

```bash
# Messaging mode (Discord/Telegram)
TELEGRAM_BOT_TOKEN=... ./gradlew run

# ACP mode (IDE integration)
./gradlew shadowJar
java -jar build/libs/moatbot-kotlin-0.1.0-SNAPSHOT-all.jar --acp
```

## Bot Commands

Available in Telegram and Discord:

| Command | Description |
|---------|-------------|
| `/clear` | Clear conversation history |
| `/status` | Show session info |
| `/help` | Show help message |
| `/start` | Welcome message (Telegram only) |