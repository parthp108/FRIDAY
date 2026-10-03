package com.health.friday.nutrition

import com.health.friday.util.DayRange

data class DayTotals(
    val dayStart: Long,
    val mealCount: Int,
    val calories: Int,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double,
    val waterMl: Int
)

class NutritionReportBuilder(
    private val nutritionRepository: NutritionRepository,
    private val waterRepository: WaterRepository
) {

    suspend fun lastDays(
        count: Int
    ): List<DayTotals> {

        val ranges = DayRange.lastDays(count)

        val from = ranges.first().first
        val to = ranges.last().second

        val meals =
            nutritionRepository.getMealsBetween(from, to)

        val water =
            waterRepository.getEntriesBetween(from, to)

        return ranges.map { range ->

            val dayMeals =
                meals.filter {
                    it.timestamp >= range.first &&
                            it.timestamp < range.second
                }

            val dayWater =
                water.filter {
                    it.timestamp >= range.first &&
                            it.timestamp < range.second
                }

            DayTotals(
                dayStart = range.first,
                mealCount = dayMeals.size,
                calories = dayMeals.sumOf { it.calories },
                protein = dayMeals.sumOf { it.protein },
                carbohydrates = dayMeals.sumOf { it.carbohydrates },
                fat = dayMeals.sumOf { it.fat },
                waterMl = dayWater.sumOf { it.amountMl }
            )
        }
    }
}