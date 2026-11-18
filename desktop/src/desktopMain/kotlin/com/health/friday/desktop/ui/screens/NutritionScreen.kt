package com.health.friday.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.ui.components.StatCard
import com.health.friday.shared.models.FridayMealDto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopNutritionScreen(
    meals: List<FridayMealDto>,
    onLogMeal: (String, Int, Float, Float, Float) -> Unit
) {
    var dayOffset by remember { mutableStateOf(0) }
    var showLogMealDialog by remember { mutableStateOf(false) }

    val calories = meals.sumOf { it.calories }
    val protein = meals.sumOf { it.protein.toDouble() }.toFloat()
    val carbs = meals.sumOf { it.carbs.toDouble() }.toFloat()
    val fat = meals.sumOf { it.fat.toDouble() }.toFloat()

    val calorieGoal = 2000
    val proteinGoal = 150f
    val carbsGoal = 250f
    val fatGoal = 65f

    val dayLabel = when (dayOffset) {
        0 -> "Today"
        -1 -> "Yesterday"
        else -> SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault()).format(Date(System.currentTimeMillis() + dayOffset * 86400000L))
    }

    if (showLogMealDialog) {
        var mealName by remember { mutableStateOf("") }
        var calText by remember { mutableStateOf("") }
        var pText by remember { mutableStateOf("25") }
        var cText by remember { mutableStateOf("40") }
        var fText by remember { mutableStateOf("12") }

        AlertDialog(
            onDismissRequest = { showLogMealDialog = false },
            title = { Text("Log Meal to Phone Database", color = Color.White) },
            containerColor = Color(0xFF1E293B),
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = mealName,
                        onValueChange = { mealName = it },
                        label = { Text("Meal / Food Name", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    OutlinedTextField(
                        value = calText,
                        onValueChange = { calText = it },
                        label = { Text("Calories (kcal)", color = Color(0xFF94A3B8)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = pText, onValueChange = { pText = it }, label = { Text("Protein (g)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                        OutlinedTextField(value = cText, onValueChange = { cText = it }, label = { Text("Carbs (g)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                        OutlinedTextField(value = fText, onValueChange = { fText = it }, label = { Text("Fat (g)") }, modifier = Modifier.weight(1f), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cal = calText.toIntOrNull() ?: 0
                        if (mealName.isNotBlank() && cal > 0) {
                            onLogMeal(mealName, cal, pText.toFloatOrNull() ?: 0f, cText.toFloatOrNull() ?: 0f, fText.toFloatOrNull() ?: 0f)
                            showLogMealDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Save Meal")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogMealDialog = false }) {
                    Text("Cancel", color = Color(0xFF94A3B8))
                }
            }
        )
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // DAY SELECTOR BAR
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { dayOffset -= 1 }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Day", tint = Color.White)
                    }
                    Text(text = dayLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = { if (dayOffset < 0) dayOffset += 1 }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Day", tint = if (dayOffset < 0) Color.White else Color(0xFF475569))
                    }
                }
            }
        }

        // ENERGY & MACROS SUMMARY GRID
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("ENERGY CONSUMED", if (calories > 0) "$calories kcal" else "—", "Goal: $calorieGoal kcal", Color(0xFFF97316), (calories.toFloat() / calorieGoal).coerceIn(0f, 1f), Modifier.weight(1.5f))
                StatCard("PROTEIN", if (protein > 0) "${protein.toInt()}g" else "—", "Goal: ${proteinGoal.toInt()}g", Color(0xFF4ADE80), (protein / proteinGoal).coerceIn(0f, 1f), Modifier.weight(1f))
                StatCard("CARBS", if (carbs > 0) "${carbs.toInt()}g" else "—", "Goal: ${carbsGoal.toInt()}g", Color(0xFF38BDF8), (carbs / carbsGoal).coerceIn(0f, 1f), Modifier.weight(1f))
                StatCard("FAT", if (fat > 0) "${fat.toInt()}g" else "—", "Goal: ${fatGoal.toInt()}g", Color(0xFFA855F7), (fat / fatGoal).coerceIn(0f, 1f), Modifier.weight(1f))
            }
        }

        // MICRO-NUTRIENTS BREAKDOWN MATRIX
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Micro-Nutrient Breakdown Matrix", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MicroBadge("FIBER", "—", Color(0xFF4ADE80))
                        MicroBadge("SUGAR", "—", Color(0xFFF97316))
                        MicroBadge("SAT FAT", "—", Color(0xFFEF4444))
                        MicroBadge("CHOLESTEROL", "—", Color(0xFFA855F7))
                        MicroBadge("SODIUM", "—", Color(0xFF38BDF8))
                        MicroBadge("POTASSIUM", "—", Color(0xFF4ADE80))
                    }
                }
            }
        }

        // MEALS LOGGED LIST (+ LOG MEAL BUTTON)
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Meals Logged (${meals.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Button(onClick = { showLogMealDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Log Meal")
                }
            }
        }

        if (meals.isEmpty()) {
            item {
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No meals logged for $dayLabel. Click '+ Log Meal' to add.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(meals) { meal ->
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Restaurant, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = meal.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(text = "Protein: ${meal.protein}g • Carbs: ${meal.carbs}g • Fat: ${meal.fat}g", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            }
                        }
                        Text(text = "${meal.calories} kcal", color = Color(0xFFF97316), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MicroBadge(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(text = label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
