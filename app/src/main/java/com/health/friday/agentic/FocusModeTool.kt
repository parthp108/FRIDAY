package com.health.friday.agentic

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter

class FocusModeTool(
    private val systemController: SystemController
) : AiTool {
    override val name = "start_focus_mode"
    override val description =
        "Autonomously prepares the user's phone for deep work by turning on Do Not Disturb and optionally setting a focus timer. " +
            "Use state=off to turn Do Not Disturb back off."
    override val parameters = listOf(
        AiToolParameter("duration_minutes", "The length of the focus session in minutes. If omitted, no timer is set.", required = false),
        AiToolParameter("intent", "What the user is focusing on (e.g. 'coding', 'reading').", required = false),
        AiToolParameter("state", "'on' (default) or 'off'.", required = false)
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val state = arguments["state"]?.trim()?.lowercase() ?: "on"

        if (state in setOf("off", "false", "disable", "end", "stop")) {
            val ok = systemController.toggleDoNotDisturb(false)
            return if (ok) "Focus Mode ended. Do Not Disturb: DISABLED"
            else dndPermissionHint("disable")
        }

        val duration = arguments["duration_minutes"]?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val intent = arguments["intent"]?.trim()?.takeIf { it.isNotEmpty() } ?: "Focus Session"

        val dndSuccess = systemController.toggleDoNotDisturb(true)
        if (!dndSuccess && !systemController.hasDndAccess()) systemController.openDndAccessSettings()
        val timerSuccess = if (duration != null) {
            systemController.setTimer(duration, "FRIDAY: $intent Complete")
        } else null

        return buildString {
            if (duration != null) {
                append("Focus Mode initiated for $duration minutes ($intent).\n")
            } else {
                append("Focus Mode initiated ($intent).\n")
            }
            if (dndSuccess) append("- Do Not Disturb: ENABLED\n")
            else append("- Do Not Disturb: FAILED. ${dndPermissionHint("enable")}\n")
            if (timerSuccess != null) {
                if (timerSuccess) append("- Timer set for $duration minutes.\n")
                else append("- Timer setup FAILED.\n")
            }
        }
    }

    private fun dndPermissionHint(verb: String) =
        "Could not $verb Do Not Disturb. FRIDAY needs Do Not Disturb access: " +
            "Settings > Notifications > Do Not Disturb access > FRIDAY."
}
