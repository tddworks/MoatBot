# MoatBot

MoatBot is an AI assistant that connects Claude to your favorite platforms. Chat with Claude through Discord, Telegram, or your IDE.

## Quick Start

### What You Need

1. **Claude CLI** - MoatBot uses the Claude CLI under the hood
   ```bash
   # Install Claude CLI (if not already installed)
   npm install -g @anthropic-ai/claude-code
   ```

2. **JDK 21+** - Required to run MoatBot
   ```bash
   # Check your Java version
   java -version
   ```

3. **Platform tokens** (at least one):
   - Discord Bot Token, or
   - Telegram Bot Token, or
   - IDE with ACP support

---

## Running MoatBot

### Option 1: Chat via Discord

Perfect for team collaboration or personal use in Discord servers.

```bash
# Set your Discord bot token
export DISCORD_BOT_TOKEN="your-discord-bot-token"

# Run MoatBot
./gradlew run
```

**Getting a Discord Bot Token:**
1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application
3. Go to "Bot" section → "Reset Token" → Copy the token
4. Enable "Message Content Intent" under Privileged Gateway Intents
5. Invite the bot to your server using OAuth2 URL Generator

**Using the bot:**
- Mention the bot or DM it directly
- Type naturally - it's just a conversation with Claude
- Use `/clear` to start fresh
- Use `/help` for commands

---

### Option 2: Chat via Telegram

Great for mobile access to Claude.

```bash
# Set your Telegram bot token
export TELEGRAM_BOT_TOKEN="your-telegram-bot-token"

# Run MoatBot
./gradlew run
```

**Getting a Telegram Bot Token:**
1. Message [@BotFather](https://t.me/botfather) on Telegram
2. Send `/newbot` and follow the prompts
3. Copy the token BotFather gives you

**Using the bot:**
- Start a chat with your bot
- Send `/start` to begin
- Type naturally to chat with Claude

---

### Option 3: Use in Your IDE (ACP)

Connect Claude to your IDE for coding assistance.

```bash
# Build the fat jar (includes all dependencies)
./gradlew shadowJar

# Run as ACP agent (stdio transport)
java -jar build/libs/moatbot-kotlin-0.1.0-SNAPSHOT-all.jar --acp
```

**IDE Configuration:**

For IDEs that support ACP (Agent Client Protocol), configure the agent:

```json
{
  "agents": {
    "moatbot": {
      "command": "java",
      "args": ["-jar", "/path/to/moatbot-kotlin-0.1.0-SNAPSHOT-all.jar", "--acp"]
    }
  }
}
```

---

## Configuration

All configuration is done via environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DISCORD_BOT_TOKEN` | Your Discord bot token | (required for Discord) |
| `TELEGRAM_BOT_TOKEN` | Your Telegram bot token | (required for Telegram) |
| `SESSION_DIR` | Directory to persist conversations (use `:memory:` for in-memory) | `~/.moatbot/sessions` |
| `CLAUDE_CLI_PATH` | Path to Claude CLI | `claude` |
| `CLAUDE_WORKING_DIR` | Working directory for Claude | `.` |

**Example with both platforms:**
```bash
export DISCORD_BOT_TOKEN="discord-token-here"
export TELEGRAM_BOT_TOKEN="telegram-token-here"

./gradlew run
```

---

## Commands

Available in all platforms:

| Command | Description |
|---------|-------------|
| `/clear` | Clear conversation history and start fresh |
| `/status` | Show current session info |
| `/help` | Show available commands |

---

## Architecture

MoatBot has a simple 3-layer architecture:

```
┌─────────────────────────────────────────────────────────┐
│                    Your Platform                         │
│   Discord  │  Telegram  │  IDE (ACP)                    │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                      MoatBot                             │
│                                                          │
│   moatbot.chat(key, userId, message)                    │
│        → Flow<ResponseEvent>                             │
│                                                          │
│   • Manages conversations                                │
│   • Streams responses                                    │
│   • Runs tools                                           │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                     Claude CLI                           │
│                                                          │
│   Executes prompts, maintains context                    │
└─────────────────────────────────────────────────────────┘
```

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/your-username/moatbot.git
cd moatbot

# Build
./gradlew build

# Run tests
./gradlew test

# Create distribution
./gradlew distZip
```

---

## Troubleshooting

### "Claude CLI not found"
Make sure Claude CLI is installed and in your PATH:
```bash
which claude
# or
claude --version
```

### "No messaging platform configured"
You need at least one platform token. Set `DISCORD_BOT_TOKEN` or `TELEGRAM_BOT_TOKEN`.

### Discord bot not responding
1. Check that "Message Content Intent" is enabled in Discord Developer Portal
2. Make sure the bot has permissions to read/send messages in the channel
3. Check the logs for errors

### Conversation not persisting
Conversations are stored in `~/.moatbot/sessions` by default. Make sure the directory is writable:
```bash
mkdir -p ~/.moatbot/sessions
```

To use in-memory storage instead (conversations lost on restart):
```bash
export SESSION_DIR=":memory:"
```

---

## License

MIT
