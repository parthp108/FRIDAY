
package com.health.friday.tasks

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.data.local.Goal
import com.health.friday.device.DeviceUsageRepository
import com.health.friday.device.PhoneUsageCard
import com.health.friday.ui.components.ScreenHeader
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayGreen
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayOrange
import com.health.friday.ui.theme.FridayText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun TasksScreen(
    todoRepository: TodoRepository,
    goalRepository: GoalRepository,
    usageRepository: DeviceUsageRepository,
    usageRefreshKey: Int,
    onOpenJournal: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    val todosFlow = remember(todoRepository) {
        todoRepository.getTodos()
    }

    val goalsFlow = remember(goalRepository) {
        goalRepository.getGoals()
    }

    val todos by todosFlow.collectAsState(initial = emptyList())
    val goals by goalsFlow.collectAsState(initial = emptyList())

    var todoInput by remember {
        mutableStateOf("")
    }

    var goalInput by remember {
        mutableStateOf("")
    }

    var selectedGoalDate by remember {
        mutableStateOf<Long?>(null)
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

        // ---------- HEADER ----------

        item {
            ScreenHeader(
                title = "Tasks",
                subtitle = "Your day, your list, your head"
            )
        }

        // ---------- DEVICE USAGE ----------

        item {
            PhoneUsageCard(
                repository = usageRepository,
                refreshKey = usageRefreshKey
            )
        }

        // ---------- TODO ----------

        item {
            SectionLabel(
                title = "TODO",
                count = todos.size
            )
        }

        item {
            AddRow(
                placeholder = "What needs to be done?",
                value = todoInput,
                onValueChange = {
                    todoInput = it
                },
                onAdd = {
                    val title = todoInput
                    todoInput = ""

                    scope.launch {
                        todoRepository.addTodo(title)
                    }
                }
            )
        }

        if (todos.isEmpty()) {
            item {
                EmptyHint("No tasks yet. Add something to get started.")
            }
        }

        items(
            items = todos,
            key = { "todo-${it.id}" }
        ) { todo ->

            CheckRow(
                title = todo.title,
                done = todo.isDone,
                onToggle = { checked ->
                    scope.launch {
                        todoRepository.setDone(todo, checked)
                    }
                },
                onDelete = {
                    scope.launch {
                        todoRepository.deleteTodo(todo)
                    }
                }
            )
        }

        // ---------- GOALS ----------

        item {
            SectionLabel(
                title = "GOALS",
                subtitle = "LONGER TERM",
                count = goals.size
            )
        }

        item {
            GoalAddArea(
                goalInput = goalInput,
                onGoalInputChange = {
                    goalInput = it
                },
                selectedDate = selectedGoalDate,
                onDateSelected = {
                    selectedGoalDate = it
                },
                onClearDate = {
                    selectedGoalDate = null
                },
                onAdd = {
                    val title = goalInput
                    val targetDate = selectedGoalDate

                    goalInput = ""
                    selectedGoalDate = null

                    scope.launch {
                        goalRepository.addGoal(
                            title = title,
                            targetDate = targetDate
                        )
                    }
                }
            )
        }

        if (goals.isEmpty()) {
            item {
                EmptyHint("No goals yet. Define something worth pursuing.")
            }
        }

        items(
            items = goals,
            key = { "goal-${it.id}" }
        ) { goal ->

            GoalRow(
                goal = goal,
                onToggle = { checked ->
                    scope.launch {
                        goalRepository.setDone(goal, checked)
                    }
                },
                onDelete = {
                    scope.launch {
                        goalRepository.deleteGoal(goal)
                    }
                }
            )
        }

        // ---------- JOURNAL ----------

        item {
            SectionLabel(
                title = "JOURNAL"
            )
        }

        item {
            JournalCard(
                onClick = onOpenJournal
            )
        }
    }
}

@Composable
private fun SectionLabel(
    title: String,
    subtitle: String? = null,
    count: Int? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 10.dp,
                bottom = 2.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                color = FridayText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = FridayMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        if (count != null) {
            Text(
                text = count.toString(),
                color = FridayCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        FridayCyan.copy(alpha = 0.12f)
                    )
                    .padding(
                        horizontal = 9.dp,
                        vertical = 5.dp
                    )
            )
        }
    }
}

@Composable
private fun EmptyHint(
    text: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard.copy(alpha = 0.55f)
        )
    ) {
        Text(
            text = text,
            color = FridayMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 15.dp
            )
        )
    }
}

@Composable
private fun AddRow(
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    text = placeholder,
                    color = FridayMuted,
                    fontSize = 14.sp
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FridayCyan,
                unfocusedBorderColor = FridayMuted.copy(alpha = 0.35f),
                focusedTextColor = FridayText,
                unfocusedTextColor = FridayText,
                cursorColor = FridayCyan
            )
        )

        Button(
            onClick = onAdd,
            enabled = value.isNotBlank(),
            modifier = Modifier.height(56.dp),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(
                horizontal = 18.dp
            ),
            colors = ButtonDefaults.buttonColors(
                containerColor = FridayCyan,
                contentColor = FridayBackground,
                disabledContainerColor = FridayCard,
                disabledContentColor = FridayMuted
            )
        ) {
            Text(
                text = "ADD",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
private fun GoalAddArea(
    goalInput: String,
    onGoalInputChange: (String) -> Unit,
    selectedDate: Long?,
    onDateSelected: (Long) -> Unit,
    onClearDate: () -> Unit,
    onAdd: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        AddRow(
            placeholder = "What are you working toward?",
            value = goalInput,
            onValueChange = onGoalInputChange,
            onAdd = onAdd
        )

        GoalDateButton(
            selectedDate = selectedDate,
            onDateSelected = onDateSelected,
            onClearDate = onClearDate
        )
    }
}

@Composable
private fun GoalDateButton(
    selectedDate: Long?,
    onDateSelected: (Long) -> Unit,
    onClearDate: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val calendar = remember {
        Calendar.getInstance()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Button(
            onClick = {
                val current = Calendar.getInstance()

                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->

                        val selected = Calendar.getInstance().apply {
                            set(
                                year,
                                month,
                                dayOfMonth,
                                0,
                                0,
                                0
                            )
                            set(Calendar.MILLISECOND, 0)
                        }

                        onDateSelected(
                            selected.timeInMillis
                        )
                    },
                    current.get(Calendar.YEAR),
                    current.get(Calendar.MONTH),
                    current.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FridayCard,
                contentColor = FridayText
            ),
            contentPadding = PaddingValues(
                horizontal = 15.dp,
                vertical = 13.dp
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = if (selectedDate == null) {
                        "TARGET DATE"
                    } else {
                        "TARGET DATE"
                    },
                    color = FridayMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Text(
                    text = selectedDate?.let {
                        formatGoalDate(it)
                    } ?: "Set a date",
                    color = if (selectedDate == null) {
                        FridayMuted
                    } else {
                        FridayCyan
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }

        if (selectedDate != null) {
            TextButton(
                onClick = onClearDate,
                contentPadding = PaddingValues(
                    horizontal = 8.dp
                )
            ) {
                Text(
                    text = "CLEAR",
                    color = FridayMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun GoalRow(
    goal: Goal,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val status = goal.targetDate?.let {
        goalDateStatus(
            targetDate = it,
            isDone = goal.isDone
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
            modifier = Modifier.fillMaxWidth()
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 7.dp,
                        end = 6.dp,
                        top = 6.dp,
                        bottom = 4.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Checkbox(
                    checked = goal.isDone,
                    onCheckedChange = onToggle,
                    colors = CheckboxDefaults.colors(
                        checkedColor = FridayGreen,
                        uncheckedColor = FridayMuted,
                        checkmarkColor = FridayBackground
                    )
                )

                Text(
                    text = goal.title,
                    color = if (goal.isDone) {
                        FridayMuted
                    } else {
                        FridayText
                    },
                    fontSize = 15.sp,
                    fontWeight = if (goal.isDone) {
                        FontWeight.Normal
                    } else {
                        FontWeight.Medium
                    },
                    textDecoration = if (goal.isDone) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = 4.dp,
                            end = 6.dp
                        )
                )

                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text(
                        text = "×",
                        color = FridayMuted,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light
                    )
                }
            }

            if (goal.targetDate != null) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 52.dp,
                            end = 18.dp,
                            bottom = 14.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = "TARGET",
                            color = FridayMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = formatGoalDate(
                                goal.targetDate
                            ),
                            color = FridayText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Text(
                        text = status ?: "",
                        color = goalStatusColor(
                            goal.targetDate,
                            goal.isDone
                        ),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    done: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 7.dp,
                    end = 6.dp,
                    top = 6.dp,
                    bottom = 6.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Checkbox(
                checked = done,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = FridayGreen,
                    uncheckedColor = FridayMuted,
                    checkmarkColor = FridayBackground
                )
            )

            Text(
                text = title,
                color = if (done) {
                    FridayMuted
                } else {
                    FridayText
                },
                fontSize = 15.sp,
                fontWeight = if (done) {
                    FontWeight.Normal
                } else {
                    FontWeight.Medium
                },
                textDecoration = if (done) {
                    TextDecoration.LineThrough
                } else {
                    null
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = 4.dp,
                        end = 6.dp
                    )
            )

            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(8.dp)
            ) {
                Text(
                    text = "×",
                    color = FridayMuted,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
private fun JournalCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(17.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {

                Text(
                    text = "Journal",
                    color = FridayText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Write it out and keep your entries.",
                    color = FridayMuted,
                    fontSize = 13.sp
                )
            }

            Text(
                text = "›",
                color = FridayOrange,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}

private fun formatGoalDate(
    millis: Long
): String {
    return SimpleDateFormat(
        "dd MMM yyyy",
        Locale.getDefault()
    ).format(millis)
}

private fun startOfDay(
    millis: Long
): Calendar {
    return Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

private fun daysUntil(
    targetDate: Long
): Long {
    val today = startOfDay(
        System.currentTimeMillis()
    )

    val target = startOfDay(
        targetDate
    )

    return TimeUnit.MILLISECONDS.toDays(
        target.timeInMillis - today.timeInMillis
    )
}

private fun goalDateStatus(
    targetDate: Long,
    isDone: Boolean
): String {

    if (isDone) {
        return "COMPLETED"
    }

    val days = daysUntil(targetDate)

    return when {
        days > 1L -> "$days days remaining"
        days == 1L -> "Tomorrow"
        days == 0L -> "Due today"
        days == -1L -> "1 day overdue"
        else -> "${-days} days overdue"
    }
}

private fun goalStatusColor(
    targetDate: Long,
    isDone: Boolean
) = when {
    isDone -> FridayGreen
    daysUntil(targetDate) < 0L -> FridayOrange
    daysUntil(targetDate) <= 3L -> FridayOrange
    else -> FridayCyan
}

