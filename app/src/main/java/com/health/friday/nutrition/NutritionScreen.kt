package com.health.friday.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val NutriInk = Color(0xFF10151B)
private val NutriRule = Color(0xFF303943)
private val NutriSoft = Color(0xFFB8C2CC)
private val NutriViolet = Color(0xFFB7A5F8)

private data class MicroNutrientStyle(
    val key: String,
    val label: String,
    val unit: String,
    val accent: Color
)

private val microNutrientStyles = listOf(
    MicroNutrientStyle("fiber", "FIBER", "g", FridayGreen),
    MicroNutrientStyle("sugar", "SUGAR", "g", FridayOrange),
    MicroNutrientStyle("saturated_fat", "SAT. FAT", "g", FridayRed),
    MicroNutrientStyle("trans_fat", "TRANS FAT", "g", FridayRed),
    MicroNutrientStyle("monounsaturated_fat", "MONO FAT", "g", FridayCyan),
    MicroNutrientStyle("polyunsaturated_fat", "POLY FAT", "g", FridayCyan),
    MicroNutrientStyle("cholesterol", "CHOLESTEROL", "mg", NutriViolet),
    MicroNutrientStyle("sodium", "SODIUM", "mg", FridayCyan),
    MicroNutrientStyle("potassium", "POTASSIUM", "mg", NutriViolet),
    MicroNutrientStyle("calcium", "CALCIUM", "mg", NutriSoft),
    MicroNutrientStyle("iron", "IRON", "mg", FridayOrange),
    MicroNutrientStyle("vitamin_a", "VITAMIN A", "µg", FridayGreen),
    MicroNutrientStyle("vitamin_c", "VITAMIN C", "mg", FridayGreen)
)

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

    var dayOffset by remember { mutableIntStateOf(0) }
    var showLogMealDialog by remember { mutableStateOf(false) }

    val selectedStart = DayRange.shiftDays(todayStart, dayOffset)

    val mealsFlow = remember(repository, selectedStart) {
        repository.observeMealsForDay(selectedStart)
    }

    val waterFlow = remember(waterRepository, selectedStart) {
        waterRepository.observeEntriesForDay(selectedStart)
    }

    val meals by mealsFlow.collectAsState(initial = emptyList())
    val waterEntries by waterFlow.collectAsState(initial = emptyList())

    val calories = meals.sumOf { it.calories }
    val protein = meals.sumOf { it.protein }
    val carbs = meals.sumOf { it.carbohydrates }
    val fat = meals.sumOf { it.fat }
    val waterMl = waterEntries.sumOf { it.amountMl }

    val nutrientTotals = remember(meals) {
        meals.calculateDailyNutrientTotals()
    }

    val calorieGoal = NutritionGoals.CALORIES
    val waterGoal = NutritionGoals.WATER_ML

    val calorieProgress =
        (calories.toFloat() / calorieGoal).coerceIn(0f, 1f)

    val waterProgress =
        (waterMl.toFloat() / waterGoal).coerceIn(0f, 1f)

    val dateText = remember(selectedStart) {
        SimpleDateFormat(
            "EEEE, dd MMMM",
            Locale.getDefault()
        ).format(Date(selectedStart))
    }

    val dayLabel = when (dayOffset) {
        0 -> "Today"
        -1 -> "Yesterday"
        else -> dateText
    }

    if (showLogMealDialog) {
        LogMealDialog(
            repository = repository,
            onDismiss = { showLogMealDialog = false },
            onMealLogged = {
                // Instantly updated via Room Flow
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FridayBackground),
        contentPadding = PaddingValues(
            start = 20.dp,
            top = 20.dp,
            end = 20.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            ScreenHeader(
                title = "Nutrition",
                subtitle = dateText
            )
        }

        item {
            DaySelector(
                label = dayLabel,
                canGoForward = dayOffset < 0,
                onPrevious = { dayOffset -= 1 },
                onNext = { dayOffset += 1 }
            )
        }

        item {
            EnergySummary(
                calories = calories,
                goal = calorieGoal,
                progress = calorieProgress
            )
        }

        item {
            MacroSummary(
                protein = protein,
                carbs = carbs,
                fat = fat
            )
        }

        item {
            SectionHeading(
                title = "More nutrients",
                detail = "From logged food data"
            )
        }

        item {
            MicroSummary(totals = nutrientTotals)
        }

        item {
            Text(
                text = "≈ indicates a partial total: one or more logged meals may lack this nutrient's data. — means no usable value is available.",
                color = FridayMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
        }

        item {
            SectionHeading(
                title = "Meals",
                detail = "${meals.size} entries",
                actionLabel = "+ Log Meal",
                onAction = { showLogMealDialog = true }
            )
        }

        if (meals.isEmpty()) {
            item {
                EmptyMealState(onLogMealClick = { showLogMealDialog = true })
            }
        } else {
            items(
                items = meals,
                key = { it.id }
            ) { meal ->
                MealRow(
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
            SectionHeading(
                title = "Hydration",
                detail = "Water intake"
            )
        }

        item {
            WaterSummary(
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
private fun LogMealDialog(
    repository: NutritionRepository,
    onDismiss: () -> Unit,
    onMealLogged: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(1) } // 1: MealType, 2: FoodSearch, 3: Portion
    var selectedMealType by remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val initial = when {
            hour in 5..10 -> "Breakfast"
            hour in 11..15 -> "Lunch"
            hour in 16..21 -> "Dinner"
            else -> "Snack"
        }
        mutableStateOf(initial)
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UsdaFoodItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var selectedFood by remember { mutableStateOf<UsdaFoodItem?>(null) }
    var quantityText by remember { mutableStateOf("1") }
    var selectedUnit by remember { mutableStateOf("piece") }
    var previewResult by remember { mutableStateOf<NutritionResult?>(null) }

    LaunchedEffect(searchQuery, selectedMealType) {
        isSearching = true
        searchResults = repository.searchUsdaFoods(searchQuery, mealType = selectedMealType)
        isSearching = false
    }

    LaunchedEffect(selectedFood, quantityText, selectedUnit) {
        val qty = quantityText.toDoubleOrNull() ?: 1.0
        selectedFood?.let { food ->
            previewResult = repository.previewNutrition(food.description, qty, selectedUnit)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FridayCard,
        titleContentColor = FridayText,
        textContentColor = FridayText,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (step) {
                        1 -> "Select Meal Type"
                        2 -> "Search USDA Food"
                        else -> "Specify Portion"
                    },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = FridayText
                )

                Text(
                    text = "Step $step/3",
                    fontSize = 11.sp,
                    color = FridayCyan,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    1 -> {
                        Text(
                            text = "When are you having this meal?",
                            fontSize = 13.sp,
                            color = FridayMuted
                        )

                        listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { mealType ->
                            val isSelected = selectedMealType == mealType
                            val bg = if (isSelected) FridayCyan.copy(alpha = 0.15f) else FridayCardLight
                            val border = if (isSelected) FridayCyan else FridayCardLight

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .border(1.dp, border, RoundedCornerShape(12.dp))
                                    .clickable {
                                        selectedMealType = mealType
                                        searchResults = emptyList()
                                        scope.launch {
                                            isSearching = true
                                            searchResults = repository.searchUsdaFoods(searchQuery, mealType = mealType)
                                            isSearching = false
                                        }
                                        step = 2
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = mealType,
                                    color = if (isSelected) FridayCyan else FridayText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (isSelected) {
                                    Text("✓", color = FridayCyan, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    2 -> {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search foods e.g. eggs, rice, chicken...", fontSize = 13.sp, color = FridayMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FridayCyan,
                                unfocusedBorderColor = FridayCardLight,
                                focusedTextColor = FridayText,
                                unfocusedTextColor = FridayText
                            )
                        )

                        Text(
                            text = if (searchQuery.isBlank()) "POPULAR $selectedMealType.uppercase() SUGGESTIONS" else "SEARCH RESULTS",
                            fontSize = 10.sp,
                            color = FridayCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        if (searchResults.isEmpty()) {
                            Text(
                                text = if (isSearching) "Searching USDA database..." else "No foods found. Try typing 'egg', 'chapati', or 'chicken'.",
                                fontSize = 12.sp,
                                color = FridayMuted,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(searchResults, key = { it.fdcId }) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(FridayCardLight)
                                            .clickable {
                                                selectedFood = item
                                                step = 3
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = item.description,
                                            color = FridayText,
                                            fontSize = 13.sp,
                                            maxLines = 2,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Text(
                                            text = "Select ›",
                                            color = FridayCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Step 3: Portion
                        Text(
                            text = selectedFood?.description ?: "Selected Food",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FridayCyan
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = quantityText,
                                onValueChange = { quantityText = it },
                                label = { Text("Quantity") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = FridayCyan,
                                    unfocusedBorderColor = FridayCardLight,
                                    focusedTextColor = FridayText,
                                    unfocusedTextColor = FridayText
                                )
                            )

                            // Units
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1.5f)
                            ) {
                                items(listOf("piece", "g", "cup", "bowl", "plate", "serving")) { unit ->
                                    val isSelected = selectedUnit == unit
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) FridayCyan.copy(alpha = 0.2f) else FridayCardLight)
                                            .border(1.dp, if (isSelected) FridayCyan else FridayCardLight, RoundedCornerShape(8.dp))
                                            .clickable { selectedUnit = unit }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = unit,
                                            color = if (isSelected) FridayCyan else FridayMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        // Preview Card
                        previewResult?.let { res ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(FridayBackground)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "NUTRITION PREVIEW",
                                    fontSize = 9.sp,
                                    color = FridayCyan,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                Text(
                                    text = "${res.calories.roundToInt()} kcal",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FridayText
                                )

                                Text(
                                    text = "Protein: ${res.protein.roundToInt()}g  •  Carbs: ${res.carbohydrates.roundToInt()}g  •  Fat: ${res.fat.roundToInt()}g",
                                    fontSize = 11.sp,
                                    color = FridayMuted
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step == 3) {
                Button(
                    onClick = {
                        val qty = quantityText.toDoubleOrNull() ?: 1.0
                        selectedFood?.let { food ->
                            scope.launch {
                                repository.logFoods(
                                    foods = listOf(FoodItem(name = food.description, quantity = qty, unit = selectedUnit)),
                                    mealType = selectedMealType
                                )
                                onMealLogged()
                                onDismiss()
                            }
                        }
                    }
                ) {
                    Text("Log Meal")
                }
            } else if (step == 2 && selectedFood != null) {
                Button(onClick = { step = 3 }) {
                    Text("Next")
                }
            }
        },
        dismissButton = {
            if (step > 1) {
                OutlinedButton(onClick = { step -= 1 }) {
                    Text("Back")
                }
            } else {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun DaySelector(
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
            onClick = onPrevious,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text("‹", color = FridayCyan, fontSize = 25.sp)
        }

        Text(
            text = label,
            color = FridayText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        TextButton(
            onClick = onNext,
            enabled = canGoForward,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Text(
                text = "›",
                color = if (canGoForward) FridayCyan else FridayMuted,
                fontSize = 25.sp
            )
        }
    }
}

@Composable
private fun EnergySummary(
    calories: Int,
    goal: Int,
    progress: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(NutriInk)
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(19.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "CALORIES CONSUMED",
                    color = NutriSoft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = calories.toString(),
                        color = FridayText,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp
                    )

                    Text(
                        text = " kcal",
                        color = FridayCyan,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 5.dp, bottom = 8.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "$goal",
                    color = FridayText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "DAILY GOAL",
                    color = NutriSoft,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = FridayCyan,
            trackColor = NutriRule
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "${(progress * 100).toInt()}% of goal",
                color = NutriSoft,
                fontSize = 11.sp
            )

            Text(
                text = if (calories <= goal) {
                    "${goal - calories} kcal left"
                } else {
                    "${calories - goal} kcal over"
                },
                color = if (calories <= goal) FridayGreen else FridayRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun MacroSummary(
    protein: Double,
    carbs: Double,
    fat: Double
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        MacroValue(
            label = "PROTEIN",
            value = protein,
            accent = FridayGreen,
            modifier = Modifier.weight(1f)
        )

        MacroDivider()

        MacroValue(
            label = "CARBS",
            value = carbs,
            accent = FridayCyan,
            modifier = Modifier.weight(1f)
        )

        MacroDivider()

        MacroValue(
            label = "FAT",
            value = fat,
            accent = FridayOrange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MacroValue(
    label: String,
    value: Double,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 25.dp, height = 3.dp)
                .clip(CircleShape)
                .background(accent)
        )

        Text(
            text = "${value.toInt()} g",
            color = FridayText,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = label,
            color = FridayMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun MacroDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .size(width = 1.dp, height = 55.dp)
            .background(NutriRule)
    )
}

@Composable
private fun MicroSummary(
    totals: List<DailyNutrientTotal>
) {
    val totalsByKey = remember(totals) {
        totals.associateBy { it.key }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        microNutrientStyles.chunked(3).forEach { rowNutrients ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowNutrients.forEach { style ->
                    MicroValue(
                        style = style,
                        total = totalsByKey[style.key],
                        modifier = Modifier.weight(1f)
                    )
                }

                repeat(3 - rowNutrients.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MicroValue(
    style: MicroNutrientStyle,
    total: DailyNutrientTotal?,
    modifier: Modifier = Modifier
) {
    val displayValue = total?.value?.let { value ->
        val formatted = if (value >= 100.0) {
            String.format(Locale.getDefault(), "%.0f", value)
        } else {
            String.format(Locale.getDefault(), "%.1f", value)
        }

        val prefix = if (total.complete) "" else "≈ "
        "$prefix$formatted ${style.unit}"
    } ?: "— ${style.unit}"

    Column(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(FridayCard)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 3.dp)
                .clip(CircleShape)
                .background(style.accent)
        )

        Text(
            text = displayValue,
            color = FridayText,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )

        Text(
            text = style.label,
            color = FridayMuted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.1.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun SectionHeading(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = FridayText,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.4).sp,
            modifier = Modifier.weight(1f)
        )

        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = actionLabel,
                    color = FridayCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text(
                text = detail,
                color = FridayMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MealRow(
    meal: Meal,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FridayCardLight),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = meal.name.take(1).uppercase(),
                    color = FridayCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = meal.name,
                    color = FridayText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = if (meal.isEstimated) {
                        "${meal.mealType} · estimated"
                    } else {
                        meal.mealType
                    },
                    color = FridayMuted,
                    fontSize = 11.sp
                )

                Text(
                    text = "P ${meal.protein.toInt()}g   " +
                            "C ${meal.carbohydrates.toInt()}g   " +
                            "F ${meal.fat.toInt()}g",
                    color = FridayMuted,
                    fontSize = 10.sp
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${meal.calories}",
                    color = FridayText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "kcal",
                    color = FridayMuted,
                    fontSize = 10.sp
                )

                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(
                        horizontal = 2.dp,
                        vertical = 0.dp
                    )
                ) {
                    Text(
                        text = "Remove",
                        color = FridayMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(NutriRule)
        )
    }
}

@Composable
private fun EmptyMealState(onLogMealClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "No meals logged today",
            color = FridayText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        Text(
            text = "Log your food using USDA search or tell FRIDAY via voice.",
            color = FridayMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )

        Button(
            onClick = onLogMealClick,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text("+ Log Meal")
        }
    }
}

@Composable
private fun WaterSummary(
    waterMl: Int,
    goalMl: Int,
    progress: Float,
    entries: List<WaterEntry>,
    onDelete: (WaterEntry) -> Unit
) {
    val timeFormat = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FridayCard)
            .padding(17.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "WATER INTAKE",
                    color = FridayCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(
                            Locale.getDefault(),
                            "%.1f",
                            waterMl / 1000.0
                        ),
                        color = FridayText,
                        fontSize = 29.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = " L",
                        color = FridayMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
            }

            Text(
                text = "${(progress * 100).toInt()}%",
                color = FridayCyan,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(CircleShape),
            color = FridayBlue,
            trackColor = FridayCardLight
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Daily target",
                color = FridayMuted,
                fontSize = 11.sp
            )

            Text(
                text = String.format(
                    Locale.getDefault(),
                    "%.1f L",
                    goalMl / 1000.0
                ),
                color = FridayText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (entries.isEmpty()) {
            Text(
                text = "No water logged yet.",
                color = FridayMuted,
                fontSize = 12.sp
            )
        } else {
            entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormat.format(Date(entry.timestamp)),
                        color = FridayMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${entry.amountMl} ml",
                        color = FridayText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    TextButton(
                        onClick = { onDelete(entry) },
                        contentPadding = PaddingValues(
                            horizontal = 8.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Text(
                            text = "✕",
                            color = FridayMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
