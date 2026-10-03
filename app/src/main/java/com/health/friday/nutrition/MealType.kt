package com.health.friday.nutrition

import java.util.Calendar

object MealType {

    private val breakfastWord = Regex("\\bbreakfast\\b")
    private val lunchWord = Regex("\\blunch\\b")
    private val dinnerWord = Regex("\\b(dinner|supper)\\b")
    private val snackWord = Regex("\\bsnacks?\\b")

    // Words in the sentence win; otherwise the time of day decides.
    fun resolve(
        text: String,
        nowMillis: Long = System.currentTimeMillis()
    ): String {

        val lower = text.lowercase()

        return when {
            breakfastWord.containsMatchIn(lower) -> "Breakfast"
            lunchWord.containsMatchIn(lower) -> "Lunch"
            dinnerWord.containsMatchIn(lower) -> "Dinner"
            snackWord.containsMatchIn(lower) -> "Snack"
            else -> byHour(nowMillis)
        }
    }

    private fun byHour(nowMillis: Long): String {

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMillis

        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        return when {
            hour < 5 -> "Snack"
            hour < 11 -> "Breakfast"
            hour < 15 -> "Lunch"
            hour < 18 -> "Snack"
            else -> "Dinner"
        }
    }
}