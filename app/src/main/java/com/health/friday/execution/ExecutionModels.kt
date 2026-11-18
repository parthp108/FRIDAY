package com.health.friday.execution

enum class ExecutionTier {
    TIER_1_APP_FUNCTIONS,
    TIER_2_REMOTE_INPUT,
    TIER_3_DEEP_LINK,
    TIER_4_ACCESSIBILITY_FALLBACK
}

data class ExecutionRequest(
    val actionName: String,
    val packageName: String,
    val queryParams: Map<String, String>,
    val textPayload: String? = null,
    val targetElementId: Int? = null
)

data class ExecutionResult(
    val success: Boolean,
    val tierUsed: ExecutionTier,
    val message: String
)
