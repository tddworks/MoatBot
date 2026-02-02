package com.moatbot.infrastructure

import com.moatbot.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Claude CLI implementation of AiProvider.
 * Executes the Claude CLI and returns AI events.
 */
class ClaudeCli(
    private val cliPath: String = "claude",
    private val workingDir: String = ".",
    private val timeoutSeconds: Long = 300
) : AiProvider {

    private val logger = LoggerFactory.getLogger(ClaudeCli::class.java)

    override fun complete(messages: List<Message>, sessionId: String?): Flow<AiEvent> = flow {
        // Get the last user message content
        val prompt = messages
            .filterIsInstance<Message.User>()
            .lastOrNull()
            ?.content

        if (prompt == null) {
            emit(AiEvent.Error("No user message found"))
            return@flow
        }

        logger.debug("Sending prompt to Claude CLI: ${prompt.take(100)}...")

        val result = execute(prompt, sessionId)

        if (result.isSuccess()) {
            logger.debug("Claude CLI response received (${result.output.length} chars)")
            emit(AiEvent.Text(result.output))
            result.sessionId?.let { emit(AiEvent.SessionId(it)) }
            emit(AiEvent.Done)
        } else {
            logger.error("Claude CLI failed with exit code ${result.exitCode}")
            emit(AiEvent.Error(result.output))
        }
    }

    private suspend fun execute(prompt: String, sessionId: String?): CliResult = withContext(Dispatchers.IO) {
        val command = buildCommand(prompt, sessionId)
        logger.info("Executing: ${command.joinToString(" ")}")

        try {
            val processBuilder = ProcessBuilder(command)
                .directory(File(workingDir))
                .redirectErrorStream(true)

            val process = processBuilder.start()

            // Close stdin immediately - we don't need to send any input
            process.outputStream.close()

            logger.info("Process started PID=${process.pid()}, waiting...")

            // Read output using a simple blocking approach with timeout
            val outputBuilder = StringBuilder()
            val reader = process.inputStream.bufferedReader()

            // Start a thread to read output
            val readerThread = Thread {
                try {
                    reader.forEachLine { line ->
                        outputBuilder.appendLine(line)
                        logger.debug("Claude output: $line")
                    }
                } catch (e: Exception) {
                    logger.debug("Reader interrupted: ${e.message}")
                }
            }
            readerThread.start()

            // Wait for process with timeout
            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                logger.warn("Timeout after ${timeoutSeconds}s, killing process")
                process.destroyForcibly()
                readerThread.interrupt()
                return@withContext CliResult(
                    output = "Process timed out after ${timeoutSeconds} seconds",
                    exitCode = -1,
                    sessionId = sessionId
                )
            }

            // Wait for reader to finish
            readerThread.join(5000)

            val output = outputBuilder.toString().trim()
            val exitCode = process.exitValue()

            logger.info("Process completed: exitCode=$exitCode, outputLength=${output.length}")

            if (exitCode != 0) {
                logger.warn("Claude CLI failed: $output")
            }

            CliResult(
                output = output,
                exitCode = exitCode,
                sessionId = parseSessionId(output) ?: sessionId
            )
        } catch (e: Exception) {
            logger.error("Failed to execute Claude CLI", e)
            CliResult(
                output = "Failed to execute: ${e.message}",
                exitCode = -1,
                sessionId = sessionId
            )
        }
    }

    private fun buildCommand(prompt: String, sessionId: String?): List<String> {
        val args = mutableListOf(
            cliPath,
            "--print",
            "--dangerously-skip-permissions"
        )

        sessionId?.let {
            args.addAll(listOf("--resume", it))
        }

        args.addAll(listOf("-p", prompt))

        return args
    }

    private fun parseSessionId(output: String): String? {
        // Session ID parsing would depend on the actual Claude CLI output format
        return null
    }
}

private data class CliResult(
    val output: String,
    val exitCode: Int,
    val sessionId: String?
) {
    fun isSuccess(): Boolean = exitCode == 0
}
