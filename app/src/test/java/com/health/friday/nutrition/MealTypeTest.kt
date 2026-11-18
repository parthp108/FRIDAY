package com.health.friday.nutrition

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class MealTypeTest {

    @Test
    fun testExplicitMealTypeOverridesTimeOfDay() {
        // Create 10:00 PM (22:00) timestamp at night
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
        }
        val nightMillis = cal.timeInMillis

        // When text mentions "lunch" explicitly at 10 PM night:
        val resultLunch = MealType.resolve("I forgot to enter lunch, for lunch I had 3 rotis, dal and rice", nightMillis)
        assertEquals("Lunch", resultLunch)

        // When text mentions "breakfast" explicitly at 10 PM night:
        val resultBreakfast = MealType.resolve("for breakfast I had 2 idlis and milk", nightMillis)
        assertEquals("Breakfast", resultBreakfast)
    }

    @Test
    fun testImplicitMealTypeResolvesByTimeOfDay() {
        // 8:00 AM -> Breakfast
        val cal8AM = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 8) }.timeInMillis
        assertEquals("Breakfast", MealType.resolve("I had 2 rotis and sabji", cal8AM))

        // 1:00 PM (13:00) -> Lunch
        val cal1PM = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 13) }.timeInMillis
        assertEquals("Lunch", MealType.resolve("I had 3 rotis and dal", cal1PM))

        // 9:00 PM (21:00) -> Dinner
        val cal9PM = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 21) }.timeInMillis
        assertEquals("Dinner", MealType.resolve("I had 3 rotis, dal, sabji and rice", cal9PM))
    }
}
