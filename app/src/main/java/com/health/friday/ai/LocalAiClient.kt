package com.health.friday.ai

class LocalAiClient : AiClient {

    override suspend fun sendMessage(
        messages: List<AiMessage>,
        context: AiContext
    ): AiResponse {

        val message =
            messages
                .lastOrNull {
                    it.role == "user"
                }
                ?.content
                ?.lowercase()
                ?: ""

        val response =
            when {
                message.contains("hello") ||
                        message.contains("hi") -> {
                    "FRIDAY online. What do you need?"
                }

                message.contains("who are you") -> {
                    "I'm FRIDAY. Your personal assistant."
                }

                message.contains("health") -> {
                    "Health systems are being connected. Once connected, I will be able to reason over your health data here."
                }

                message.contains("nutrition") ||
                        message.contains("food") ||
                        message.contains("calorie") -> {
                    "Nutrition will be handled through FRIDAY's nutrition tools. The AI will understand what you say and update the nutrition data."
                }

                message.contains("screen time") ||
                        message.contains("gaming") -> {
                    "Device intelligence is coming next. FRIDAY will be able to analyze screen time and app usage."
                }

                else -> {
                    "FRIDAY is ready. My intelligence system is being connected to your personal data."
                }
            }

        return AiResponse(
            message = response
        )
    }
}