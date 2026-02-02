# MoatBot

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org/)

MoatBot is an open-source AI assistant that connects [Claude](https://claude.ai) to your favorite platforms. Chat with Claude through Discord, Telegram, or your IDE.

## Features

- **Multi-platform**: Discord, Telegram, and IDE support via ACP
- **Conversation persistence**: Continue where you left off
- **Streaming responses**: Real-time message updates
- **Built on Claude CLI**: Leverages the official Claude Code CLI

## Quick Start

### Prerequisites

1. **Claude CLI** - MoatBot uses the Claude CLI under the hood
   ```bash
   npm install -g @anthropic-ai/claude-code
   ```

2. **JDK 21+** - Required to run MoatBot
   ```bash
   java -version
   ```

3. **Platform token** (at least one):
   - Discord Bot Token, or
   - Telegram Bot Token, or
   - IDE with ACP support

### Running MoatBot

#### Discord

```bash
export DISCORD_BOT_TOKEN="your-discord-bot-token"
./gradlew run
```

**Setup:**
1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application → Bot section → Reset Token → Copy
3. Enable "Message Content Intent" under Privileged Gateway Intents
4. Invite bot using OAuth2 URL Generator

#### Telegram

```bash
export TELEGRAM_BOT_TOKEN="your-telegram-bot-token"
./gradlew run
```

**Setup:**
1. Message [@BotFather](https://t.me/botfather) on Telegram
2. Send `/newbot` and follow the prompts
3. Copy the token

#### IDE (ACP)

```bash
./gradlew shadowJar
java -jar build/libs/moatbot-kotlin-0.1.0-SNAPSHOT-all.jar --acp
```

## Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `DISCORD_BOT_TOKEN` | Discord bot token | - |
| `TELEGRAM_BOT_TOKEN` | Telegram bot token | - |
| `SESSION_DIR` | Conversation storage (`:memory:` for in-memory) | `~/.moatbot/sessions` |
| `CLAUDE_CLI_PATH` | Path to Claude CLI | `claude` |
| `CLAUDE_WORKING_DIR` | Working directory for Claude | `.` |

## Commands

| Command | Description |
|---------|-------------|
| `/clear` | Clear conversation history |
| `/status` | Show session info |
| `/help` | Show available commands |

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Your Platform                         │
│   Discord  │  Telegram  │  IDE (ACP)                    │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                      MoatBot                             │
│   • Manages conversations                                │
│   • Streams responses                                    │
│   • Handles multiple platforms                           │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                     Claude CLI                           │
│   Executes prompts, maintains context                    │
└─────────────────────────────────────────────────────────┘
```

## Building from Source

```bash
git clone https://github.com/anthropics/moatbot.git
cd moatbot

./gradlew build
./gradlew test
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Troubleshooting

**Claude CLI not found**
```bash
which claude  # Verify installation
```

**No messaging platform configured**
Set at least one of `DISCORD_BOT_TOKEN` or `TELEGRAM_BOT_TOKEN`.

**Discord bot not responding**
1. Enable "Message Content Intent" in Discord Developer Portal
2. Verify bot has read/send message permissions

## License

MIT