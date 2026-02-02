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

# Run with Discord/Telegram
DISCORD_BOT_TOKEN="..." ./gradlew run
TELEGRAM_BOT_TOKEN="..." ./gradlew run

# Run as ACP agent (for IDE integration)
./gradlew shadowJar
java -jar build/libs/moatbot-kotlin-0.1.0-SNAPSHOT-all.jar --acp
```

## Architecture

MoatBot connects Claude CLI to messaging platforms (Discord, Telegram) and IDEs (via ACP).

### Layer Structure

```
domain/          Pure domain model (no external dependencies)
application/     Orchestration (Moatbot class), ports
infrastructure/  Implementations (ClaudeCli, gateways, repositories)
```

### Core Flow

1. **Gateways** (TelegramGateway, DiscordGateway, AcpAgent) receive messages from platforms
2. **Moatbot** orchestrates: manages conversations, streams AI responses, handles tools
3. **ClaudeCli** executes `claude --print -p <prompt>` subprocess and returns results
4. Responses flow back through gateways to the user

### Key Types

- `SessionKey` - Identifies a conversation (userId:chatId)
- `Conversation` - Aggregate root with messages and current turn state
- `AiProvider` - Interface for AI backends (implemented by ClaudeCli)
- `ResponseEvent` - Sealed class for streaming events (TextChunk, ToolStarted, ToolCompleted, Completed, Failed)
- `MessageClient` - Port interface for messaging platforms

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