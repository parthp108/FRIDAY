package com.health.friday.ai

class AiToolRegistry(
    private val tools: List<AiTool> = emptyList()
) {

    fun getTool(
        name: String
    ): AiTool? {
        return tools.firstOrNull {
            it.name == name
        }
    }

    fun getTools(): List<AiTool> {
        return tools
    }
}