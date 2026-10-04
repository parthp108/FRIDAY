package com.health.friday.alarms

import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter

class AlarmTool(
    private val repository: AlarmRepository
) : AiTool {


    override val name: String = "set_alarm"

    override val description: String =
        "Set a normal alarm at a specified time. The alarm can be one-time or repeat daily."

    override val parameters: List<AiToolParameter> =
        listOf(
            AiToolParameter(
                name = "title",
                description = "What the alarm is for.",
                required = true
            ),
            AiToolParameter(
                name = "timeMillis",
                description = "The alarm time as Unix epoch milliseconds.",
                required = true
            ),
            AiToolParameter(
                name = "repeatDaily",
                description = "Whether the alarm should repeat every day. Use true or false.",
                required = true
            )
        )

    override suspend fun execute(
        arguments: Map<String, String>
    ): String {

        val title = arguments["title"]?.trim()

        val timeMillis = arguments["timeMillis"]?.toLongOrNull()

        val repeatDaily = arguments["repeatDaily"]
            ?.trim()
            ?.lowercase()
            ?.let {
                when (it) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }

        if (title.isNullOrEmpty()) {
            return "I need an alarm title."
        }

        if (timeMillis == null) {
            return "I need a valid alarm time."
        }

        if (repeatDaily == null) {
            return "I need to know whether the alarm should repeat daily."
        }

        val alarm = repository.addAlarm(
            title = title,
            timeMillis = timeMillis,
            repeatDaily = repeatDaily
        )

        if (alarm == null) {
            return "I couldn't create the alarm."
        }

        if (!alarm.enabled) {
            return "The alarm was saved but could not be scheduled."
        }

        return if (alarm.repeatDaily) {
            "Alarm set for \"$title\" and repeating daily."
        } else {
            "Alarm set for \"$title\"."
        }
    }


}
