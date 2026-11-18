package com.health.friday.desktop.features.widgets

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.ui.components.WidgetContainer
import com.health.friday.shared.models.FridayTaskDto

@Composable
fun TaskTrackerWidget(
    tasks: List<FridayTaskDto>,
    onToggle: (FridayTaskDto) -> Unit,
    config: WidgetConfig? = null,
    modifier: Modifier = Modifier
) {
    val pendingTodos = tasks.filter { !it.isCompleted }

    WidgetContainer(title = "Pending Task Checklist", config = config, modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
        ) {
            if (pendingTodos.isNotEmpty()) {
                items(pendingTodos, key = { it.id }) { todo ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(0xFF0B0F19), RoundedCornerShape(8.dp))
                            .clickable { onToggle(todo) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = todo.isCompleted,
                            onCheckedChange = { onToggle(todo) },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00E5FF))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = todo.title,
                            color = if (todo.isCompleted) Color.Gray else Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                item {
                    Text("No pending tasks", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        }
    }
}
