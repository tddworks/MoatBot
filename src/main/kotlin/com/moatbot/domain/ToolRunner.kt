package com.moatbot.domain

/**
 * Interface for running tool calls.
 */
interface ToolRunner {
    /**
     * Run a tool call and return the result.
     */
    suspend fun run(call: ToolCall): ToolResult

    /**
     * Get the list of available tools.
     */
    fun availableTools(): List<ToolDefinition>
}

/**
 * Result of a tool execution.
 */
data class ToolResult(
    val output: String,
    val isError: Boolean = false
)
