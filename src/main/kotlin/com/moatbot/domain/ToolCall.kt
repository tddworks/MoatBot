package com.moatbot.domain

import kotlinx.serialization.Serializable

@Serializable
data class ToolCall(
    val id: ToolCallId,
    val name: String,
    val arguments: Map<String, String>
) {
    companion object {
        fun create(name: String, arguments: Map<String, String>): ToolCall =
            ToolCall(
                id = ToolCallId.generate(),
                name = name,
                arguments = arguments
            )
    }
}

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ParameterDefinition> = emptyMap()
)

data class ParameterDefinition(
    val description: String,
    val required: Boolean = false
)
