package com.health.friday.v4.router

import com.health.friday.v4.TrinityMeshClient

enum class ExecutionGear {
    GEAR_1_REGEX,
    GEAR_2_PC_GPU,
    GEAR_3_PHONE_NPU,
    GEAR_4_CLOUD_GEMINI
}

data class RouterResponse(
    val reply: String,
    val gearUsed: ExecutionGear
)

class CognitiveRouter(
    private val meshClient: TrinityMeshClient
) {
    suspend fun routeQuery(query: String, systemPrompt: String): RouterResponse {
        val lower = query.trim().lowercase()
        if (lower == "ping") {
            return RouterResponse(
                reply = "FRIDAY System Operational. All gears ready.",
                gearUsed = ExecutionGear.GEAR_1_REGEX
            )
        }
        return RouterResponse(
            reply = "Processing via local gear...",
            gearUsed = ExecutionGear.GEAR_3_PHONE_NPU
        )
    }
}
