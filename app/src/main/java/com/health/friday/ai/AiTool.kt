package com.health.friday.ai

interface AiTool {

    val name: String

    val description: String

    suspend fun execute(
        arguments: Map<String, String>
    ): String
}