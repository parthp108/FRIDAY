package com.health.friday.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.health.friday.nutrition.NutritionGoals
import com.health.friday.nutrition.NutritionRepository
import com.health.friday.nutrition.WaterRepository
import com.health.friday.ui.components.ScreenHeader
import com.health.friday.ui.components.StatCard
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayBlue
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayGreen
import com.health.friday.ui.theme.FridayOrange
import com.health.friday.ui.theme.FridayRed
import com.health.friday.util.DayRange
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    repository: NutritionRepository,
    waterRepository: WaterRepository,
    modifier: Modifier = Modifier
) {

    val mealsFlow = remember(repository) {
        repository.observeTodayMeals()
    }

    val waterFlow = remember(waterRepository) {
        waterRepository.observeTodayEntries()
    }

    val dayStartFlow = remember {
        DayRange.todayFlow().map { it.first }
    }

    val meals by mealsFlow.collectAsState(initial = emptyList())
    val waterEntries by waterFlow.collectAsState(initial = emptyList())
    val dayStart by dayStartFlow.collectAsState(
        initial = DayRange.today().first
    )

    val calories = meals.sumOf { it.calories }
    val waterMl = waterEntries.sumOf { it.amountMl }

    val dateText = remember(dayStart) {
        SimpleDateFormat(
            "EEEE, dd MMMM",
            Locale.getDefault()
        ).format(Date(dayStart))
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FridayBackground),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 18.dp,
            end = 18.dp,
            bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {
            ScreenHeader(
                title = "Home",
                subtitle = dateText
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "CALORIES",
                    value = "$calories kcal",
                    detail = "of ${NutritionGoals.CALORIES} goal",
                    accent = FridayOrange,
                    progress = (calories.toFloat() / NutritionGoals.CALORIES)
                        .coerceIn(0f, 1f),
                    modifier = Modifier.weight(1f)
                )

                StatCard(
                    label = "WATER",
                    value = String.format(
                        Locale.getDefault(),
                        "%.1f L",
                        waterMl / 1000f
                    ),
                    detail = "of ${NutritionGoals.WATER_ML / 1000f} L goal",
                    accent = FridayCyan,
                    progress = (waterMl.toFloat() / NutritionGoals.WATER_ML)
                        .coerceIn(0f, 1f),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "STEPS",
                    value = "—",
                    detail = "Not connected yet",
                    accent = FridayGreen,
                    modifier = Modifier.weight(1f)
                )

                StatCard(
                    label = "HEART",
                    value = "—",
                    detail = "Not connected yet",
                    accent = FridayRed,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "GYM",
                    value = "—",
                    detail = "Not tracked yet",
                    accent = FridayBlue,
                    modifier = Modifier.weight(1f)
                )

                StatCard(
                    label = "TODO",
                    value = "—",
                    detail = "See Tasks tab",
                    accent = FridayCyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}