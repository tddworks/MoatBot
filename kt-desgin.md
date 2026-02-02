# MoatBot Kotlin POC - Implementation Plan

## Overview

Build a Kotlin 2.3.0 POC that receives messages from Telegram/Discord, sends them to Claude Code via subprocess, and returns results to the user.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐
│    Telegram     │     │     Discord     │
└────────┬────────┘     └────────┬────────┘
         │                       │
         └───────────┬───────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │   MessageClient Port  │  ← Infrastructure
         └───────────┬───────────┘
                     │
                     ▼
         ┌───────────────────────┐
         │       Gateway         │  ← Application
         │  (Orchestration)      │
         └───────────┬───────────┘
                     │
         ┌───────────┴───────────┐
         │                       │
         ▼                       ▼
┌─────────────────┐   ┌─────────────────┐
│    Provider     │   │  SessionStore   │  ← Ports
└────────┬────────┘   └─────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│     Claude CLI (Direct)         │  ← Spawn claude command
└─────────────────────────────────┘
```

## Project Structure

```
moatbot-kotlin/
├── build.gradle.kts
├── settings.gradle.kts
│
├── src/main/kotlin/com/moatbot/
│   ├── domain/                    # Pure domain model
│   │   ├── model/
│   │   │   ├── ValueObjects.kt    # UserId, ChatId, SessionKey
│   │   │   ├── Message.kt         # Sealed class: User, Assistant, ToolUse, ToolResult
│   │   │   └── Session.kt         # Session aggregate
│   │   ├── result/
│   │   │   └── ProviderResult.kt  # Sealed class: Success, ToolUse, Error
│   │   └── event/
│   │       └── DomainEvents.kt    # MessageAdded, SessionCreated, etc.
│   │
│   ├── application/               # Orchestration
│   │   ├── port/
│   │   │   ├── Provider.kt        # AI provider interface
│   │   │   ├── SessionStore.kt    # Session persistence interface
│   │   │   └── MessageClient.kt   # Messaging platform interface
│   │   └── service/
│   │       ├── Gateway.kt         # Main orchestration service
│   │       └── ToolRegistry.kt    # Tool management
│   │
│   ├── infrastructure/            # Implementations
│   │   ├── provider/
│   │   │   └── ClaudeCodeProvider.kt
│   │   ├── store/
│   │   │   ├── MemorySessionStore.kt
│   │   │   └── FileSessionStore.kt
│   │   ├── messaging/
│   │   │   ├── telegram/
│   │   │   │   └── TelegramClient.kt
│   │   │   └── discord/
│   │   │       └── DiscordClient.kt
│   │   └── cli/
│   │       └── ClaudeCli.kt       # Direct claude CLI subprocess
│   │
│   └── Main.kt                    # Entry point
│
└── src/test/kotlin/com/moatbot/
    ├── domain/
    ├── application/
    └── infrastructure/
```

## Implementation Phases

### Phase 1: Domain Layer

1. `ValueObjects.kt` - UserId, ChatId, SessionKey (inline value classes)
2. `Message.kt` - Sealed class hierarchy for messages
3. `Session.kt` - Session aggregate with message history
4. `ProviderResult.kt` - Success, ToolUse, Error sealed class
5. `DomainEvents.kt` - Domain events

### Phase 2: Application Layer

1. `Provider.kt` - Provider port interface
2. `SessionStore.kt` - Session store port interface
3. `MessageClient.kt` - Messaging client port interface
4. `ToolRegistry.kt` - Tool registration and invocation
5. `Gateway.kt` - Main orchestration with completion loop

### Phase 3: Infrastructure - Core

1. `ClaudeCli.kt` - Direct claude CLI subprocess wrapper
2. `ClaudeCliProvider.kt` - Provider using claude CLI with --print flag
3. `MemorySessionStore.kt` - In-memory session storage
4. `FileSessionStore.kt` - File-based persistence

### Phase 4: Infrastructure - Messaging

1. `TelegramClient.kt` - Telegram bot integration
2. `DiscordClient.kt` - Discord bot integration

### Phase 5: Integration

1. `Main.kt` - Wire everything together
2. Configuration via environment variables
3. End-to-end testing

## Key Domain Types

```kotlin
// Value Objects
@JvmInline value class UserId(val value: String)
@JvmInline value class ChatId(val value: String)
@JvmInline value class SessionKey(val value: String)

// Messages (sealed class)
sealed class Message {
    data class User(val content: String, ...) : Message()
    data class Assistant(val content: String, ...) : Message()
    data class AssistantToolUse(val toolCalls: List<ToolCall>, ...) : Message()
    data class ToolResult(val toolCallId: ToolCallId, val content: String, ...) : Message()
}

// Provider Result (sealed class)
sealed class ProviderResult {
    data class Success(val text: String) : ProviderResult()
    data class ToolUse(val calls: List<ToolCall>) : ProviderResult()
    data class Error(val code: ErrorCode, val message: String) : ProviderResult()
}
```

## Dependencies

```kotlin
// build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
}

dependencies {
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Telegram (tgbotapi)
    implementation("dev.inmo:tgbotapi:18.2.1")

    // Discord (Kord)
    implementation("dev.kord:kord-core:0.15.0")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.5.12")

    // Testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("io.mockk:mockk:1.13.13")
}
```

## Configuration

```bash
# Environment variables
TELEGRAM_BOT_TOKEN=your-telegram-token
DISCORD_BOT_TOKEN=your-discord-token
SESSION_DIR=~/.moatbot/sessions  # optional, uses memory if not set
CLAUDE_CLI_PATH=claude           # path to claude CLI (default: claude)
CLAUDE_WORKING_DIR=.             # working directory for claude CLI
```

## Claude CLI Integration

The provider will spawn the claude CLI directly:

```kotlin
// ClaudeCli.kt - Wrapper for claude CLI
class ClaudeCli(
    private val cliPath: String = "claude",
    private val workingDir: String = "."
) {
    suspend fun execute(prompt: String, sessionId: String? = null): CliResult {
        val args = mutableListOf(cliPath, "--print")
        sessionId?.let {
            args.addAll(listOf("--resume", it))
        }
        args.addAll(listOf("-p", prompt))

        val process = ProcessBuilder(args)
            .directory(File(workingDir))
            .redirectErrorStream(false)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return CliResult(output, exitCode, sessionId)
    }
}

// Usage in provider
class ClaudeCliProvider(private val cli: ClaudeCli) : Provider {
    override suspend fun complete(request: CompletionRequest): ProviderResult {
        val prompt = request.messages.lastUserContent()
        val result = cli.execute(prompt)
        return if (result.exitCode == 0) {
            ProviderResult.Success(result.output)
        } else {
            ProviderResult.Error(ErrorCode.SUBPROCESS_FAILED, result.output)
        }
    }
}
```

**Key CLI flags:**

- `--print` / `-p`: Print response without interactive mode
- `--resume <id>`: Resume existing session
- `--output-format json`: Get structured JSON output (optional)

## Verification

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run application
TELEGRAM_BOT_TOKEN=your-token ./gradlew run

# Test manually
# 1. Send message to Telegram bot
# 2. Bot forwards to Claude Code
# 3. Response returned to Telegram
```

## Files to Create

| File | Description |
|------|-------------|
| `build.gradle.kts` | Gradle build configuration |
| `settings.gradle.kts` | Project settings |
| `domain/model/ValueObjects.kt` | Value objects |
| `domain/model/Message.kt` | Message sealed class |
| `domain/model/Session.kt` | Session aggregate |
| `domain/result/ProviderResult.kt` | Result sealed class |
| `domain/event/DomainEvents.kt` | Domain events |
| `application/port/Provider.kt` | Provider interface |
| `application/port/SessionStore.kt` | Session store interface |
| `application/port/MessageClient.kt` | Messaging interface |
| `application/service/Gateway.kt` | Main gateway service |
| `application/service/ToolRegistry.kt` | Tool management |
| `infrastructure/cli/ClaudeCli.kt` | Claude CLI subprocess wrapper |
| `infrastructure/provider/ClaudeCliProvider.kt` | Claude CLI provider |
| `infrastructure/store/MemorySessionStore.kt` | Memory store |
| `infrastructure/store/FileSessionStore.kt` | File store |
| `infrastructure/messaging/telegram/TelegramClient.kt` | Telegram |
| `infrastructure/messaging/discord/DiscordClient.kt` | Discord |
| `Main.kt` | Entry point |
