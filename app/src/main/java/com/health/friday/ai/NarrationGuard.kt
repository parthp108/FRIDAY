package com.health.friday.ai

class NarrationGuard {

    fun guardResponse(reply: String, conversation: List<AiMessage>): String {
        val lowerReply = reply.lowercase()

        // Protect against correlation hallucinations
        val claimsCorrelation = listOf("correlation", "correlate", "correlates").any { it in lowerReply }

        if (claimsCorrelation) {
            val recentToolResult = conversation.takeLast(6).firstOrNull {
                it.role == "tool" && it.content.contains("Correlation between")
            }

            // If it claims a correlation without running the math tool
            if (recentToolResult == null && !lowerReply.contains("calculate") && !lowerReply.contains("run")) {
                return "I cannot make statistical claims without running the numbers. Should I calculate the exact correlation for you?"
            }

            // If it contradicts the mathematical output (e.g., tool says "Weak", model hallucinates "Strong")
            if (recentToolResult != null) {
                val toolLower = recentToolResult.content.lowercase()

                val modelSaysStrong = lowerReply.contains("strong")
                val toolSaysWeak = toolLower.contains("weak")
                val toolSaysModerate = toolLower.contains("moderate")

                if ((modelSaysStrong && toolSaysWeak) || (modelSaysStrong && toolSaysModerate)) {
                    val correlationType = if (toolSaysWeak) "weak" else "moderate"
                    return """
                        Mathematical correction: The data actually shows a $correlationType correlation, not a strong one.

                        (Raw data: ${recentToolResult.content})
                    """.trimIndent()
                }
            }
        }

        return reply
    }
}