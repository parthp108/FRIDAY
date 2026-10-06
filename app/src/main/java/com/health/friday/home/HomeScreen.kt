
package com.health.friday.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.health.friday.data.local.TodoItem
import com.health.friday.device.HealthConnectRepository
import com.health.friday.device.HealthSummary
import com.health.friday.nutrition.NutritionGoals
import com.health.friday.nutrition.NutritionRepository
import com.health.friday.nutrition.WaterRepository
import com.health.friday.tasks.TodoRepository
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    repository: NutritionRepository,
    waterRepository: WaterRepository,
    todoRepository: TodoRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val healthConnectRepository =
        remember {
            HealthConnectRepository(context)
        }

    val mealsFlow = remember(repository) {
        repository.observeTodayMeals()
    }

    val waterFlow = remember(waterRepository) {
        waterRepository.observeTodayEntries()
    }

    val todosFlow = remember(todoRepository) {
        todoRepository.getTodos()
    }

    val dayStartFlow = remember {
        DayRange.todayFlow().map { it.first }
    }

    val meals by mealsFlow.collectAsState(initial = emptyList())
    val waterEntries by waterFlow.collectAsState(initial = emptyList())
    val todos by todosFlow.collectAsState(initial = emptyList())
    val dayStart by dayStartFlow.collectAsState(
        initial = DayRange.today().first
    )

    var healthSummary by remember {
        mutableStateOf<HealthSummary?>(null)
    }

    var healthAvailable by remember {
        mutableStateOf(false)
    }

    var healthAccess by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    suspend fun refreshHealth() {
        healthAvailable =
            healthConnectRepository.isAvailable()

        if (!healthAvailable) {
            healthAccess = false
            healthSummary = null
            return
        }

        healthAccess =
            healthConnectRepository.hasAccess()

        if (!healthAccess) {
            healthSummary = null
            return
        }

        healthSummary =
            healthConnectRepository.getTodayHealth()
    }

    LaunchedEffect(Unit) {
        refreshHealth()
    }

    androidx.compose.runtime.DisposableEffect(
        lifecycleOwner
    ) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    scope.launch {
                        refreshHealth()
                    }
                }
            }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val pendingTodos =
        todos.filter { !it.isDone }

    val calories =
        meals.sumOf { it.calories }

    val waterMl =
        waterEntries.sumOf { it.amountMl }

    val dateText =
        remember(dayStart) {
            SimpleDateFormat(
                "EEEE, dd MMMM",
                Locale.getDefault()
            ).format(Date(dayStart))
        }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FridayBackground),

        contentPadding =
            PaddingValues(
                start = 18.dp,
                top = 18.dp,
                end = 18.dp,
                bottom = 96.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {
            ScreenHeader(
                title = "Home",
                subtitle = dateText
            )
        }

        // ---------------------------------------------------------
        // CALORIES + WATER
        // ---------------------------------------------------------

        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "CALORIES",

                    value =
                        "$calories kcal",

                    detail =
                        "of ${NutritionGoals.CALORIES} goal",

                    accent =
                        FridayOrange,

                    progress =
                        (
                                calories.toFloat() /
                                        NutritionGoals.CALORIES
                                )
                            .coerceIn(0f, 1f),

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "WATER",

                    value =
                        String.format(
                            Locale.getDefault(),
                            "%.1f L",
                            waterMl / 1000f
                        ),

                    detail =
                        "of ${NutritionGoals.WATER_ML / 1000f} L goal",

                    accent =
                        FridayCyan,

                    progress =
                        (
                                waterMl.toFloat() /
                                        NutritionGoals.WATER_ML
                                )
                            .coerceIn(0f, 1f),

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------------
        // STEPS + HEART
        // ---------------------------------------------------------

        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "STEPS",

                    value =
                        if (healthAccess && healthSummary != null) {
                            healthSummary!!.steps.toString()
                        } else {
                            "—"
                        },

                    detail =
                        when {
                            !healthAvailable ->
                                "Health Connect unavailable"

                            !healthAccess ->
                                "Connect in Health"

                            else ->
                                "Today"
                        },

                    accent =
                        FridayGreen,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "HEART",

                    value =
                        if (
                            healthAccess &&
                            healthSummary?.heartRate != null
                        ) {
                            healthSummary!!.heartRate.toString()
                        } else {
                            "—"
                        },

                    detail =
                        when {
                            !healthAvailable ->
                                "Health Connect unavailable"

                            !healthAccess ->
                                "Connect in Health"

                            healthSummary?.heartRate != null ->
                                "Latest bpm"

                            else ->
                                "No data"
                        },

                    accent =
                        FridayRed,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------------
        // GYM
        // ---------------------------------------------------------

        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "GYM",

                    value = "—",

                    detail = "Not tracked yet",

                    accent = FridayBlue,

                    modifier =
                        Modifier.weight(1f)
                )

                Spacer(
                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------------
        // TODO SECTION
        // ---------------------------------------------------------

        item {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "TODO",

                    modifier =
                        Modifier.padding(
                            start = 4.dp,
                            bottom = 8.dp
                        )
                )

                if (pendingTodos.isNotEmpty()) {

                    Column(
                        verticalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        pendingTodos.forEach { todo ->

                            TodoRow(
                                todo = todo,

                                onChecked = {
                                    scope.launch {
                                        todoRepository.setDone(
                                            todo,
                                            true
                                        )
                                    }
                                }
                            )
                        }
                    }

                } else {

                    Text(
                        text = "No pending TODOs",

                        modifier =
                            Modifier.padding(
                                start = 4.dp,
                                top = 2.dp
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoRow(
    todo: TodoItem,
    onChecked: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color =
                        FridayBlue.copy(
                            alpha = 0.08f
                        ),

                    shape =
                        RoundedCornerShape(14.dp)
                )
                .padding(
                    horizontal = 10.dp,
                    vertical = 6.dp
                ),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Checkbox(
            checked =
                todo.isDone,

            onCheckedChange = {
                if (it) {
                    onChecked()
                }
            },

            colors =
                CheckboxDefaults.colors(
                    checkedColor =
                        FridayCyan
                )
        )

        Spacer(
            modifier =
                Modifier.height(1.dp)
        )

        Text(
            text =
                todo.title,

            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
        )
    }
}

