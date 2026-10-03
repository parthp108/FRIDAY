package com.health.friday.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.data.local.Meal
import com.health.friday.data.local.WaterEntry
import com.health.friday.ui.components.ScreenHeader
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayBlue
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCardLight
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayGreen
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayOrange
import com.health.friday.ui.theme.FridayRed
import com.health.friday.ui.theme.FridayText
import com.health.friday.util.DayRange
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NutritionScreen(
    repository: NutritionRepository,
    waterRepository: WaterRepository,
    modifier: Modifier = Modifier
) {

    val scope = rememberCoroutineScope()

    val todayStartFlow = remember {
        DayRange.todayFlow().map { it.first }
    }

    val todayStart by todayStartFlow.collectAsState(
        initial = DayRange.today().first
    )

    // 0 = today, -1 = yesterday, and so on.
    var dayOffset by remember {
        mutableIntStateOf(0)
    }

    val selectedStart =
        DayRange.shiftDays(todayStart, dayOffset)

    val mealsFlow = remember(repository, selectedStart) {
        repository.observeMealsForDay(selectedStart)
    }

    val waterFlow = remember(waterRepository, selectedStart) {
        waterRepository.observeEntriesForDay(selectedStart)
    }

    val meals by mealsFlow.collectAsState(initial = emptyList())
    val waterEntries by waterFlow.collectAsState(initial = emptyList())

    val waterMl = waterEntries.sumOf { it.amountMl }

    val calories = meals.sumOf { it.calories }
    val protein = meals.sumOf { it.protein }
    val carbs = meals.sumOf { it.carbohydrates }
    val fat = meals.sumOf { it.fat }

    val calorieGoal = NutritionGoals.CALORIES

    val calorieProgress =
        (calories.toFloat() / calorieGoal)
            .coerceIn(0f, 1f)

    val waterGoal = NutritionGoals.WATER_ML

    val waterProgress =
        (waterMl.toFloat() / waterGoal)
            .coerceIn(0f, 1f)

    val dateText = remember(selectedStart) {
        SimpleDateFormat(
            "EEEE, dd MMMM",
            Locale.getDefault()
        ).format(Date(selectedStart))
    }

    val dayLabel =
        when (dayOffset) {
            0 -> "Today"
            -1 -> "Yesterday"
            else -> dateText
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        item {
            ScreenHeader(
                title = "Nutrition",
                subtitle = dateText
            )
        }

        item {
            DaySwitcher(
                label = dayLabel,
                canGoForward = dayOffset < 0,
                onPrevious = { dayOffset -= 1 },
                onNext = { dayOffset += 1 }
            )
        }

        item {
            CalorieCard(
                calories = calories,
                goal = calorieGoal,
                progress = calorieProgress
            )
        }

        item {
            MacroRow(
                protein = protein,
                carbs = carbs,
                fat = fat
            )
        }

        item {
            SectionTitle(
                title = "MEALS"
            )
        }

        if (meals.isEmpty()) {

            item {
                EmptyMealCard()
            }

        } else {

            items(
                items = meals,
                key = { it.id }
            ) { meal ->

                MealCard(
                    meal = meal,
                    onDelete = {
                        scope.launch {
                            repository.deleteMeal(meal)
                        }
                    }
                )
            }
        }

        item {
            SectionTitle(
                title = "HYDRATION"
            )
        }

        item {
            HydrationCard(
                waterMl = waterMl,
                goalMl = waterGoal,
                progress = waterProgress,
                entries = waterEntries,
                onDelete = { entry ->
                    scope.launch {
                        waterRepository.deleteWater(entry)
                    }
                }
            )
        }
    }
}

@Composable
private fun DaySwitcher(
    label: String,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        TextButton(
            onClick = onPrevious
        ) {
            Text(
                text = "‹",
                color = FridayCyan,
                fontSize = 24.sp
            )
        }

        Text(
            text = label,
            color = FridayText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        TextButton(
            onClick = onNext,
            enabled = canGoForward
        ) {
            Text(
                text = "›",
                color = if (canGoForward) FridayCyan else FridayMuted,
                fontSize = 24.sp
            )
        }
    }
}
@Composable
private fun CalorieCard(
    calories: Int,
    goal: Int,
    progress: Float
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {

                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(
                            FridayCyan.copy(alpha = 0.10f)
                        )
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = calories.toString(),
                        color = FridayText,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "KCAL",
                        color = FridayCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "/ $goal",
                        color = FridayMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(
                modifier = Modifier.size(18.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Daily intake",
                    color = FridayText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text =
                        if (calories <= goal) {
                            "${goal - calories} kcal remaining"
                        } else {
                            "${calories - goal} kcal over goal"
                        },
                    color =
                        if (calories <= goal) {
                            FridayGreen
                        } else {
                            FridayRed
                        },
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    color = FridayCyan,
                    trackColor = FridayCardLight
                )
            }
        }
    }
}

@Composable
private fun MacroRow(
    protein: Double,
    carbs: Double,
    fat: Double
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        MacroCard(
            modifier = Modifier.weight(1f),
            value = "${protein.toInt()}g",
            label = "PROTEIN",
            accent = FridayGreen
        )

        MacroCard(
            modifier = Modifier.weight(1f),
            value = "${carbs.toInt()}g",
            label = "CARBS",
            accent = FridayCyan
        )

        MacroCard(
            modifier = Modifier.weight(1f),
            value = "${fat.toInt()}g",
            label = "FAT",
            accent = FridayOrange
        )
    }
}

@Composable
private fun MacroCard(
    modifier: Modifier,
    value: String,
    label: String,
    accent: Color
) {

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Text(
                text = value,
                color = FridayText,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = label,
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun SectionTitle(
    title: String
) {

    Text(
        text = title,
        color = FridayMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun MealCard(
    meal: Meal,
    onDelete: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 8.dp,
                    end = 4.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(FridayCyan)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {

                Text(
                    text = meal.name,
                    color = FridayText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text =
                        if (meal.isEstimated) {
                            "${meal.mealType} · estimated"
                        } else {
                            meal.mealType
                        },
                    color = FridayMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Text(
                    text =
                        "P ${meal.protein.toInt()}g   " +
                                "C ${meal.carbohydrates.toInt()}g   " +
                                "F ${meal.fat.toInt()}g",
                    color = FridayMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Text(
                text = "${meal.calories}",
                color = FridayText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = " kcal",
                color = FridayMuted,
                fontSize = 10.sp
            )

            TextButton(
                onClick = onDelete
            ) {
                Text(
                    text = "✕",
                    color = FridayMuted
                )
            }
        }
    }
}

@Composable
private fun EmptyMealCard() {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp)
        ) {

            Text(
                text = "No meals logged",
                color = FridayText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Tell FRIDAY what you ate and it will appear here.",
                color = FridayMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun HydrationCard(
    waterMl: Int,
    goalMl: Int,
    progress: Float,
    entries: List<WaterEntry>,
    onDelete: (WaterEntry) -> Unit
) {

    val timeFormat = remember {
        SimpleDateFormat(
            "HH:mm",
            Locale.getDefault()
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = "WATER",
                    color = FridayCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text =
                        "${waterMl / 1000.0} / " +
                                "${goalMl / 1000.0} L",
                    color = FridayText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .height(7.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = FridayBlue,
                trackColor = FridayCardLight
            )

            if (entries.isEmpty()) {

                Text(
                    text = "No water logged. Tell FRIDAY what you drank.",
                    color = FridayMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )

            } else {

                for (entry in entries) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            text = timeFormat.format(Date(entry.timestamp)),
                            color = FridayMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )

                        Text(
                            text = "${entry.amountMl} ml",
                            color = FridayText,
                            fontSize = 14.sp
                        )

                        TextButton(
                            onClick = { onDelete(entry) }
                        ) {
                            Text(
                                text = "✕",
                                color = FridayMuted
                            )
                        }
                    }
                }
            }
        }
    }
}