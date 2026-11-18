package com.health.friday.shared.parsers

import com.health.friday.shared.models.FridayMealDto
import com.health.friday.shared.models.FridayReminderDto
import com.health.friday.shared.models.FridayTaskDto

object SharedMealParser {
    private val calorieRegex = Regex("""(\d+)\s*(kcal|calories|cal)""", RegexOption.IGNORE_CASE)
    private val proteinRegex = Regex("""(\d+(?:\.\d+)?)\s*g?\s*protein""", RegexOption.IGNORE_CASE)
    private val carbsRegex = Regex("""(\d+(?:\.\d+)?)\s*g?\s*carbs?""", RegexOption.IGNORE_CASE)
    private val fatRegex = Regex("""(\d+(?:\.\d+)?)\s*g?\s*fat""", RegexOption.IGNORE_CASE)

    fun parseMeal(input: String): FridayMealDto {
        val caloriesMatch = calorieRegex.find(input)
        val calories = caloriesMatch?.groupValues?.get(1)?.toIntOrNull() ?: 300
        val protein = proteinRegex.find(input)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val carbs = carbsRegex.find(input)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
        val fat = fatRegex.find(input)?.groupValues?.get(1)?.toFloatOrNull() ?: 0f

        val cleanName = input
            .replace(calorieRegex, "")
            .replace(proteinRegex, "")
            .replace(carbsRegex, "")
            .replace(fatRegex, "")
            .replace(Regex("""(?i)^(log|ate|had|add)\s+"""), "")
            .trim()
            .ifBlank { "Logged Meal" }

        return FridayMealDto(
            id = "meal_${System.currentTimeMillis()}",
            name = cleanName.capitalize(),
            calories = calories,
            protein = protein,
            carbs = carbs,
            fat = fat,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun String.capitalize(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

object SharedTaskParser {
    fun parseTask(input: String): FridayTaskDto {
        val cleanTitle = input
            .replace(Regex("""(?i)^(add|task|todo|create task)\s+"""), "")
            .trim()
            .ifBlank { "New Task" }

        return FridayTaskDto(
            id = "task_${System.currentTimeMillis()}",
            title = cleanTitle,
            isCompleted = false,
            timestamp = System.currentTimeMillis()
        )
    }
}

object SharedReminderParser {
    fun parseReminder(input: String, delayMinutes: Long = 30): FridayReminderDto {
        val cleanMessage = input
            .replace(Regex("""(?i)^(remind|reminder|set reminder)\s+"""), "")
            .trim()
            .ifBlank { "Reminder" }

        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60_000L)
        return FridayReminderDto(
            id = "rem_${System.currentTimeMillis()}",
            message = cleanMessage,
            timeMillis = triggerTime,
            isTriggered = false
        )
    }
}
