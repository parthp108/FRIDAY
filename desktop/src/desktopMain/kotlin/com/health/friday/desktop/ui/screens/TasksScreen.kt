package com.health.friday.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.ui.components.StatCard
import com.health.friday.shared.models.DailySignalDto
import com.health.friday.shared.models.FridayReminderDto
import com.health.friday.shared.models.FridayTaskDto
import com.health.friday.shared.sync.cloud.GoalSyncPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DesktopTasksScreen(
    tasks: List<FridayTaskDto>,
    reminders: List<FridayReminderDto>,
    signals: DailySignalDto,
    goals: List<GoalSyncPayload> = emptyList(),
    onAddTask: (String) -> Unit,
    onToggleTask: (FridayTaskDto) -> Unit,
    onToggleGoal: (GoalSyncPayload) -> Unit = {},
    onAddReminder: (String, Long) -> Unit
) {
    var todoInput by remember { mutableStateOf("") }
    var goalInput by remember { mutableStateOf("") }

    val pendingTasks = tasks.filter { !it.isCompleted }
    val completedTasks = tasks.filter { it.isCompleted }
    val screenMin = signals.phoneUsageMinutes

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // SECTION 1: PHONE SCREEN TIME & FOCUS USAGE CARD
        item {
            StatCard(
                label = "PHONE SCREEN TIME & FOCUS USAGE",
                value = screenMin?.let { "${it / 60}h ${it % 60}m" } ?: "—",
                detail = "Daily phone usage tracked from your phone",
                accent = Color(0xFFA855F7),
                progress = ((screenMin ?: 0) / 360f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // SECTION 2: ADD TODO TASK
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add New Todo Item", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = todoInput,
                            onValueChange = { todoInput = it },
                            placeholder = { Text("What needs to be done?", color = Color(0xFF64748B)) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF0F172A), unfocusedContainerColor = Color(0xFF0F172A))
                        )
                        Button(
                            onClick = {
                                if (todoInput.isNotBlank()) {
                                    onAddTask(todoInput)
                                    todoInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Todo")
                        }
                    }
                }
            }
        }

        // SECTION 3: PENDING TODOS CHECKLIST
        item {
            Text("Pending TODOs (${pendingTasks.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (pendingTasks.isEmpty()) {
            item {
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                        Text("No pending TODOs. You are all caught up!", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(pendingTasks) { t ->
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggleTask(t) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = false, onCheckedChange = { onToggleTask(t) }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF38BDF8)))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = t.title, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // SECTION 4: COMPLETED TODOS
        if (completedTasks.isNotEmpty()) {
            item {
                Text("Completed (${completedTasks.size})", color = Color(0xFF64748B), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            items(completedTasks) { t ->
                Surface(color = Color(0xFF0F172A), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onToggleTask(t) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = true, onCheckedChange = { onToggleTask(t) }, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4ADE80)))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = t.title, color = Color(0xFF64748B), fontSize = 14.sp, textDecoration = TextDecoration.LineThrough)
                    }
                }
            }
        }

        // SECTION 5: LONG-TERM GOALS
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Flag, contentDescription = null, tint = Color(0xFFF97316), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Long-Term Goals & Milestones", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = goalInput,
                            onValueChange = { goalInput = it },
                            placeholder = { Text("e.g. Run 10k Marathon in November", color = Color(0xFF64748B)) },
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF0F172A), unfocusedContainerColor = Color(0xFF0F172A))
                        )
                        Button(
                            onClick = {
                                if (goalInput.isNotBlank()) {
                                    onAddTask("[GOAL] $goalInput")
                                    goalInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                            modifier = Modifier.height(54.dp)
                        ) {
                            Text("Set Goal")
                        }
                    }
                    if (goals.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Saved Goals (${goals.size})", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        goals.forEach { goal ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = goal.isDone,
                                    onCheckedChange = { onToggleGoal(goal) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF97316))
                                )
                                Text(
                                    text = goal.title,
                                    color = if (goal.isDone) Color(0xFF64748B) else Color.White,
                                    fontSize = 13.sp,
                                    textDecoration = if (goal.isDone) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }
        }

        // SECTION 6: REMINDERS & SCHEDULED ALARMS
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Alarm, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scheduled Reminders & Alarms", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (reminders.isEmpty()) {
                        Text("No active reminders set.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            reminders.forEach { r ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().background(Color(0xFF0F172A), RoundedCornerShape(10.dp)).padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = r.message, color = Color.White, fontSize = 14.sp)
                                    Text(
                                        text = SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault()).format(Date(r.timeMillis)),
                                        color = Color(0xFFA855F7),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
