package com.moatbot

import com.agentclientprotocol.agent.Agent
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import com.moatbot.app.Moatbot
import com.moatbot.infrastructure.AcpAgent
import com.moatbot.infrastructure.ClaudeCli
import com.moatbot.infrastructure.DiscordGateway
import com.moatbot.infrastructure.DiscordSlashCommandHandler
import com.moatbot.infrastructure.FileConversationRepository
import com.moatbot.infrastructure.LocalToolRunner
import com.moatbot.infrastructure.MemoryConversationRepository
import com.moatbot.infrastructure.TelegramGateway
import com.moatbot.infrastructure.messaging.discord.DiscordClient
import com.moatbot.infrastructure.messaging.telegram.TelegramClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

private val logger = LoggerFactory.getLogger("com.moatbot.Main")

fun main(args: Array<String>) = runBlocking {
    val isAcpMode = args.contains("--acp")

    if (isAcpMode) {
        runAcpAgent()
    } else {
        runMessagingGateways()
    }
}

/**
 * Run as ACP agent over stdio for IDE integration.
 */
private suspend fun runAcpAgent() = coroutineScope {
    logger.info("Starting MoatBot in ACP mode...")

    val config = loadConfig()

    // Setup Moatbot
    val moatbot = createMoatbot(config)

    // Create ACP agent
    val acpAgent = AcpAgent(moatbot)

    // Setup stdio transport
    val transport = StdioTransport(
        this,
        Dispatchers.IO,
        input = System.`in`.asSource().buffered(),
        output = System.out.asSink().buffered(),
        "moatbot"
    )

    // Create and start protocol
    val protocol = Protocol(this, transport)
    Agent(protocol, acpAgent)

    logger.info("ACP agent started, waiting for connections...")
    protocol.start()
}

/**
 * Run with Discord/Telegram gateways.
 */
private suspend fun runMessagingGateways() = coroutineScope {
    logger.info("Starting MoatBot...")

    val config = loadConfig()

    // Setup Moatbot
    val moatbot = createMoatbot(config)

    // Collect gateways to start
    val gateways = mutableListOf<suspend () -> Unit>()

    config.telegramToken?.let { token ->
        logger.info("Telegram bot enabled")
        val client = TelegramClient(token)
        val gateway = TelegramGateway(moatbot, client)
        gateways.add { gateway.start() }
    }

    config.discordToken?.let { token ->
        logger.info("Discord bot enabled")
        val slashHandler = DiscordSlashCommandHandler(moatbot)
        val client = DiscordClient(token, slashCommandHandler = slashHandler)
        val gateway = DiscordGateway(moatbot, client)
        gateways.add { gateway.start() }
    }

    if (gateways.isEmpty()) {
        logger.error("No messaging platform configured. Set TELEGRAM_BOT_TOKEN or DISCORD_BOT_TOKEN")
        exitProcess(1)
    }

    // Start all gateways
    gateways.forEach { startGateway ->
        launch { startGateway() }
    }
}

private fun createMoatbot(config: Config): Moatbot {
    // Setup AI provider
    val aiProvider = ClaudeCli(
        cliPath = config.claudeCliPath,
        workingDir = config.claudeWorkingDir
    )

    // Setup conversation repository
    val conversationRepository = if (config.sessionDir == ":memory:") {
        logger.info("Using in-memory conversation repository")
        MemoryConversationRepository()
    } else {
        logger.info("Using file conversation repository at ${config.sessionDir}")
        FileConversationRepository(config.sessionDir)
    }

    // Setup tool runner
    val toolRunner = LocalToolRunner()

    return Moatbot(
        aiProvider = aiProvider,
        conversationRepository = conversationRepository,
        toolRunner = toolRunner
    )
}

data class Config(
    val telegramToken: String?,
    val discordToken: String?,
    val sessionDir: String,
    val claudeCliPath: String,
    val claudeWorkingDir: String
)

private fun loadConfig(): Config {
    val telegramToken = System.getenv("TELEGRAM_BOT_TOKEN")
    val discordToken = System.getenv("DISCORD_BOT_TOKEN")
    val sessionDir = expandPath(System.getenv("SESSION_DIR") ?: "~/.moatbot/sessions")
    val claudeCliPath = System.getenv("CLAUDE_CLI_PATH") ?: "claude"
    val claudeWorkingDir = System.getenv("CLAUDE_WORKING_DIR") ?: "."

    return Config(
        telegramToken = telegramToken,
        discordToken = discordToken,
        sessionDir = sessionDir,
        claudeCliPath = claudeCliPath,
        claudeWorkingDir = claudeWorkingDir
    )
}

private fun expandPath(path: String): String {
    return if (path.startsWith("~")) {
        System.getProperty("user.home") + path.substring(1)
    } else {
        path
    }
}
