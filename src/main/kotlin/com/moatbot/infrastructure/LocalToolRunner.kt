package com.moatbot.infrastructure

import com.moatbot.domain.*

/**
 * Local implementation of ToolRunner.
 * Executes tools registered in the runner.
 */
class LocalToolRunner : ToolRunner {

    private val tools = mutableMapOf<String, Tool>()

    /**
     * Register a tool that can be executed.
     */
    fun register(tool: Tool) {
        tools[tool.name] = tool
    }

    override suspend fun run(call: ToolCall): ToolResult {
        val tool = tools[call.name]
            ?: return ToolResult("Unknown tool: ${call.name}", isError = true)

        return try {
            val output = tool.execute(call.arguments)
            ToolResult(output)
        } catch (e: Exception) {
            ToolResult("Tool execution error: ${e.message}", isError = true)
        }
    }

    override fun availableTools(): List<ToolDefinition> =
        tools.values.map { it.definition }
}

/**
 * Interface for a tool that can be executed.
 */
interface Tool {
    val name: String
    val description: String
    val definition: ToolDefinition
        get() = ToolDefinition(name, description)

    suspend fun execute(arguments: Map<String, String>): String
}
