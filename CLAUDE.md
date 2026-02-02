# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.moatbot.domain.ConversationTest"

# Run a single test method
./gradlew test --tests "com.moatbot.domain.ConversationTest.testReceive"

# Code coverage (Kover)
./gradlew koverHtmlReport    # HTML report at build/reports/kover/html/
./gradlew koverXmlReport     # XML report for CI integration

# Run with Discord/Telegram
DISCORD_BOT_TOKEN="..." ./gradlew run
TELEGRAM_BOT_TOKEN="..." ./gradlew run

# Run as ACP agent (for IDE integration)
./gradlew shadowJar
java -jar build/libs/moatbot-kotlin-0.1.0-SNAPSHOT-all.jar --acp
```

## TDD Guidelines (Chicago School with Mockito)

Follow Test-Driven Development using the Chicago School (classicist) approach:

### Workflow

1. **Red**: Write a failing test first that describes the desired behavior
2. **Green**: Write the minimal code to make the test pass
3. **Refactor**: Improve the code while keeping tests green

### Chicago School Principles

- **Test behavior, not implementation**: Focus on observable outcomes
- **Prefer state-based testing**: Verify results via return values and state changes
- **Use real objects when practical**: Only mock external dependencies and ports
- **Start from the inside out**: Build domain layer first, then application, then infrastructure

### When to Use Mocks (Mockito)

Use `mock<Interface>()` for:
- **Ports/interfaces**: `AiProvider`, `ConversationRepository`, `ToolRunner`, `MessageClient`
- **External services**: Anything crossing system boundaries
- **Slow dependencies**: File I/O, network calls (when not testing that specific component)

Do NOT mock:
- Domain objects (`Conversation`, `Turn`, `Message`, value objects)
- Pure functions and data classes

### Test Structure Example

```kotlin
@Test
fun `should emit text chunks when AI responds`() = runTest {
    // Arrange - set up collaborators
    val repository = mock<ConversationRepository>()
    val aiProvider = mock<AiProvider>()
    whenever(aiProvider.complete(any(), any(), any())).thenReturn(
        flowOf(AiEvent.Text("Hello"), AiEvent.Done)
    )
    val moatbot = Moatbot(repository, aiProvider, toolRunner)

    // Act - execute the behavior
    val events = moatbot.chat(sessionKey, userId, "Hi").toList()

    // Assert - verify outcomes
    assertIs<ResponseEvent.TextChunk>(events[0])
    verify(repository).save(any())
}
```

### Test Organization

```
src/test/kotlin/com/moatbot/
├── domain/          # Pure unit tests, no mocks needed
├── app/             # Integration tests with mocked ports
└── infrastructure/  # Component tests (may use real files/temp dirs)
```

## Architecture

MoatBot connects Claude CLI to messaging platforms (Discord, Telegram) and IDEs (via ACP).

For detailed design, see [docs/design.md](docs/design.md).

### Package Structure

```
com.moatbot/
├── Main.kt                    # Entry point
├── app/                       # Application layer
│   └── Moatbot.kt            # Main orchestrator
├── application/port/          # Port interfaces
│   └── MessageClient.kt
├── domain/                    # Pure domain (no external deps)
│   ├── ValueObjects.kt       # ConversationId, UserId, ChatId, SessionKey, etc.
│   ├── Conversation.kt       # Aggregate root
│   ├── Turn.kt               # Active response state
│   ├── Message.kt            # Sealed class (User, Assistant, ToolUse, ToolResult)
│   ├── ToolCall.kt           # Tool definitions
│   ├── ToolRunner.kt         # Tool execution interface
│   ├── ResponseEvent.kt      # Outbound events
│   ├── AiProvider.kt         # AI backend interface + AiEvent
│   └── ConversationRepository.kt
└── infrastructure/            # Implementations
    ├── ClaudeCli.kt          # AiProvider impl (subprocess)
    ├── LocalToolRunner.kt    # ToolRunner impl
    ├── MemoryConversationRepository.kt
    ├── FileConversationRepository.kt
    ├── DiscordGateway.kt
    ├── TelegramGateway.kt
    ├── AcpAgent.kt           # IDE integration
    └── messaging/
        ├── discord/DiscordClient.kt
        └── telegram/TelegramClient.kt
```

### Core Flow

1. **Gateways** (TelegramGateway, DiscordGateway, AcpAgent) receive messages from platforms
2. **Moatbot** orchestrates: manages conversations, streams AI responses, handles tools
3. **ClaudeCli** executes `claude --print --dangerously-skip-permissions` subprocess
4. Responses flow back through gateways to the user

### Key Types

**Value Objects** (immutable identifiers):
- `ConversationId`, `UserId`, `ChatId`, `SessionKey`, `ToolCallId`, `MessageId`

**Domain Model**:
- `Conversation` - Aggregate root with messages and current Turn
- `Turn` - Accumulates text chunks and tool calls during response
- `Message` - Sealed class: `User`, `Assistant`, `AssistantToolUse`, `ToolResult`
- `ToolCall` - Tool invocation (name + arguments map)

**Ports/Interfaces**:
- `AiProvider` - AI backends (`complete()` returns `Flow<AiEvent>`)
- `ConversationRepository` - Persistence
- `ToolRunner` - Tool execution
- `MessageClient` - Messaging platforms

**Events**:
- `AiEvent` - From AI: `Text`, `ToolCall`, `SessionId`, `Done`, `Error`
- `ResponseEvent` - To clients: `TextChunk`, `ToolStarted`, `ToolCompleted`, `Completed`, `Failed`

### Entry Points

- `Main.kt` - Application entry; `--acp` flag switches to ACP mode
- `Moatbot.chat(key, userId, message)` returns `Flow<ResponseEvent>` for streaming responses

### Conversation State

Conversations use immutable state transitions:
- `receive()` → adds user message, starts new Turn
- `addTextChunk()` / `addToolCall()` / `addToolResult()` → mutate current Turn
- `complete()` → finalizes Turn into Assistant message
- `fail()` → clears Turn on error

### Configuration

Environment variables:
- `DISCORD_BOT_TOKEN` / `TELEGRAM_BOT_TOKEN` - Platform tokens
- `SESSION_DIR` - Persistence directory (`:memory:` for in-memory)
- `CLAUDE_CLI_PATH` - Path to claude CLI (default: `claude`)
- `CLAUDE_WORKING_DIR` - Working directory for CLI

## Build Configuration

Dependencies are managed via Gradle Version Catalog (`gradle/libs.versions.toml`).

- **Kotlin**: 2.3.0 (JVM target: Java 21)
- **Coroutines**: kotlinx-coroutines 1.10.2
- **Serialization**: kotlinx-serialization-json 1.8.1
- **Discord**: Kord 0.17.0
- **Telegram**: tgbotapi 30.0.2
- **ACP**: 0.15.2
- **Testing**: kotlin-test, mockito-kotlin, turbine, kotlinx-coroutines-test
- **Coverage**: Kover 0.9.1