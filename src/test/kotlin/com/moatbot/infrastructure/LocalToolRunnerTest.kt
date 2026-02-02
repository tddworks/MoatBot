package com.moatbot.infrastructure

import com.moatbot.domain.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalToolRunnerTest {

    @Test
    fun `availableTools returns empty list when no tools registered`() {
        val runner = LocalToolRunner()

        assertTrue(runner.availableTools().isEmpty())
    }

    @Test
    fun `register adds tool to available tools`() {
        val runner = LocalToolRunner()
        val tool = TestTool("test_tool", "A test tool")

        runner.register(tool)

        assertEquals(1, runner.availableTools().size)
        assertEquals("test_tool", runner.availableTools()[0].name)
        assertEquals("A test tool", runner.availableTools()[0].description)
    }

    @Test
    fun `run executes registered tool`() = runTest {
        val runner = LocalToolRunner()
        val tool = TestTool("echo", "Echoes the input") { args ->
            "Echo: ${args["message"]}"
        }
        runner.register(tool)

        val call = ToolCall.create("echo", mapOf("message" to "Hello"))
        val result = runner.run(call)

        assertEquals("Echo: Hello", result.output)
        assertFalse(result.isError)
    }

    @Test
    fun `run returns error for unknown tool`() = runTest {
        val runner = LocalToolRunner()

        val call = ToolCall.create("unknown_tool", emptyMap())
        val result = runner.run(call)

        assertTrue(result.isError)
        assertTrue(result.output.contains("Unknown tool"))
        assertTrue(result.output.contains("unknown_tool"))
    }

    @Test
    fun `run handles tool exception`() = runTest {
        val runner = LocalToolRunner()
        val tool = TestTool("failing_tool", "A tool that fails") {
            throw RuntimeException("Tool execution failed")
        }
        runner.register(tool)

        val call = ToolCall.create("failing_tool", emptyMap())
        val result = runner.run(call)

        assertTrue(result.isError)
        assertTrue(result.output.contains("Tool execution error"))
    }

    @Test
    fun `can register multiple tools`() {
        val runner = LocalToolRunner()
        val tool1 = TestTool("tool1", "First tool")
        val tool2 = TestTool("tool2", "Second tool")

        runner.register(tool1)
        runner.register(tool2)

        assertEquals(2, runner.availableTools().size)
    }

    @Test
    fun `registering tool with same name overwrites`() {
        val runner = LocalToolRunner()
        val tool1 = TestTool("test", "First version")
        val tool2 = TestTool("test", "Second version")

        runner.register(tool1)
        runner.register(tool2)

        assertEquals(1, runner.availableTools().size)
        assertEquals("Second version", runner.availableTools()[0].description)
    }

    @Test
    fun `run passes arguments to tool`() = runTest {
        val runner = LocalToolRunner()
        var receivedArgs: Map<String, String>? = null
        val tool = TestTool("capture", "Captures args") { args ->
            receivedArgs = args
            "OK"
        }
        runner.register(tool)

        val call = ToolCall.create("capture", mapOf("key1" to "value1", "key2" to "value2"))
        runner.run(call)

        assertEquals(mapOf("key1" to "value1", "key2" to "value2"), receivedArgs)
    }
}

private class TestTool(
    override val name: String,
    override val description: String,
    private val handler: suspend (Map<String, String>) -> String = { "OK" }
) : Tool {
    override suspend fun execute(arguments: Map<String, String>): String {
        return handler(arguments)
    }
}
