package com.health.friday.ai

data class AiToolParameter(
    val name: String,
    val description: String,
    val required: Boolean = false
)

interface AiTool {

    val name: String

    val description: String

    // All parameters are passed as strings.
    val parameters: List<AiToolParameter>
        get() = emptyList()

    suspend fun execute(
        arguments: Map<String, String>
    ): String
}