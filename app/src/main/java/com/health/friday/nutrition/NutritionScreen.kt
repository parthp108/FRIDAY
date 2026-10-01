package com.health.friday.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.data.local.Meal
import com.health.friday.data.local.WaterDao
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FridayBackground = Color(0xFF080B10)
private val FridayCard = Color(0xFF11161D)
private val FridayCardLight = Color(0xFF171D25)

private val FridayCyan = Color(0xFF00D9FF)
private val FridayBlue = Color(0xFF3D7CFF)
private val FridayGreen = Color(0xFF39E58C)
private val FridayOrange = Color(0xFFFFB84D)
private val FridayRed = Color(0xFFFF5C6C)

private val FridayText = Color(0xFFF2F6FA)
private val FridayMuted = Color(0xFF8D98A5)

@Composable
fun NutritionScreen(
    repository: NutritionRepository,
    waterDao: WaterDao,
    modifier: Modifier = Modifier
) {

    val calendar = remember {
        Calendar.getInstance()
    }

    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    val startOfDay = calendar.timeInMillis

    calendar.add(Calendar.DAY_OF_YEAR, 1)

    val endOfDay = calendar.timeInMillis

    val meals by repository
        .getTodayMeals(
            startOfDay = startOfDay,
            endOfDay = endOfDay
        )
        .collectAsState(initial = emptyList())

    val waterMl by waterDao
        .getTodayWater(
            startOfDay = startOfDay,
            endOfDay = endOfDay
        )
        .collectAsState(initial = 0)

    val calories = meals.sumOf { it.calories }
    val protein = meals.sumOf { it.protein }
    val carbs = meals.sumOf { it.carbohydrates }
    val fat = meals.sumOf { it.fat }

    val calorieGoal = 2500

    val calorieProgress =
        (calories.toFloat() / calorieGoal)
            .coerceIn(0f, 1f)

    val waterGoal = 2500

    val waterProgress =
        (waterMl.toFloat() / waterGoal)
            .coerceIn(0f, 1f)

    val dateText = remember {
        SimpleDateFormat(
            "EEEE, dd MMMM",
            Locale.getDefault()
        ).format(Date())
    }

    var question by remember {
        mutableStateOf("")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FridayBackground)
    ) {

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                androidx.compose.foundation.layout.PaddingValues(
                    start = 18.dp,
                    top = 18.dp,
                    end = 18.dp,
                    bottom = 28.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {

            item {

                Text(
                    text = "NUTRITION",
                    color = FridayText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = dateText,
                    color = FridayMuted,
                    fontSize = 14.sp,
                    modifier =
                        Modifier.padding(top = 3.dp)
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
                    title = "TODAY'S MEALS"
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
                        meal = meal
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
                    progress = waterProgress
                )
            }

            item {

                SectionTitle(
                    title = "FRIDAY INTELLIGENCE"
                )
            }

            item {

                FridayCard(
                    question = question,
                    onQuestionChange = {
                        question = it
                    }
                )
            }
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
            verticalAlignment =
                Alignment.CenterVertically
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
                    horizontalAlignment =
                        Alignment.CenterHorizontally
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
                    modifier =
                        Modifier.padding(top = 5.dp)
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
        horizontalArrangement =
            Arrangement.spacedBy(10.dp)
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
                modifier =
                    Modifier.padding(top = 4.dp)
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
    meal: Meal
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
                .padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically
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
                    text = meal.mealType,
                    color = FridayMuted,
                    fontSize = 12.sp,
                    modifier =
                        Modifier.padding(top = 2.dp)
                )

                Text(
                    text =
                        "P ${meal.protein.toInt()}g   " +
                                "C ${meal.carbohydrates.toInt()}g   " +
                                "F ${meal.fat.toInt()}g",
                    color = FridayMuted,
                    fontSize = 11.sp,
                    modifier =
                        Modifier.padding(top = 8.dp)
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
                text =
                    "Tell FRIDAY what you ate and it will appear here.",
                color = FridayMuted,
                fontSize = 13.sp,
                modifier =
                    Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun HydrationCard(
    waterMl: Int,
    goalMl: Int,
    progress: Float
) {

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
                horizontalArrangement =
                    Arrangement.SpaceBetween
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
        }
    }
}

@Composable
private fun FridayCard(
    question: String,
    onQuestionChange: (String) -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(FridayCyan)
                )

                Text(
                    text = "FRIDAY",
                    color = FridayCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier =
                        Modifier.padding(start = 9.dp)
                )
            }

            Text(
                text =
                    "Ask me about your nutrition.",
                color = FridayText,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier.padding(top = 12.dp)
            )

            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                placeholder = {
                    Text(
                        text = "Did I overeat today?",
                        color = FridayMuted
                    )
                },
                maxLines = 3
            )
        }
    }
}