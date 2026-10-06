
package com.health.friday.assistant

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.health.friday.ai.AiMessage
import com.health.friday.ai.AiOrchestrator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AssistantScreen(
    orchestrator: AiOrchestrator,
    initialMessages: List<AiMessage> = emptyList(),
    onMessagesChanged: (List<AiMessage>) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val messages = remember(initialMessages) {
        mutableStateListOf(
            *if (initialMessages.isNotEmpty()) {
                initialMessages.toTypedArray()
            } else {
                arrayOf(
                    AiMessage(
                        role = "assistant",
                        content = "FRIDAY online. What do you need?"
                    )
                )
            }
        )
    }

    var input by remember {
        mutableStateOf("")
    }

    var isThinking by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        onMessagesChanged(messages.toList())
    }

    LaunchedEffect(messages.size, isThinking) {

        onMessagesChanged(messages.toList())

        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF080B10))
            .navigationBarsPadding()
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 12.dp,
                    top = 20.dp,
                    bottom = 12.dp
                )
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF17202D)),
                    contentAlignment = Alignment.Center
                ) {

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF5CC8FF))
                    )
                }

                Spacer(
                    modifier = Modifier.width(12.dp)
                )

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "FRIDAY",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF45D483))
                        )

                        Spacer(
                            modifier = Modifier.width(6.dp)
                        )

                        Text(
                            text = "ONLINE",
                            color = Color(0xFF7F8A99),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                TextButton(
                    onClick = onOpenHistory
                ) {
                    Text(
                        text = "History",
                        color = Color(0xFF5CC8FF)
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Text(
                text = "Your personal assistant",
                color = Color(0xFF8B95A3),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                top = 12.dp,
                bottom = 16.dp
            )
        ) {

            items(messages) { message ->

                MessageBubble(
                    message = message
                )
            }

            if (isThinking) {

                item {
                    ThinkingBubble()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B0F15))
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 10.dp,
                    bottom = 12.dp
                )
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {

                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "Ask FRIDAY anything...",
                            color = Color(0xFF697483)
                        )
                    },
                    enabled = !isThinking,
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF141922),
                        unfocusedContainerColor = Color(0xFF141922),
                        disabledContainerColor = Color(0xFF141922),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color(0xFF697483),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        cursorColor = Color(0xFF5CC8FF)
                    )
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Button(
                    onClick = {

                        val message = input.trim()

                        if (message.isEmpty()) {
                            return@Button
                        }

                        messages.add(
                            AiMessage(
                                role = "user",
                                content = message
                            )
                        )

                        input = ""
                        isThinking = true

                        scope.launch {

                            val response =
                                orchestrator.sendMessage(
                                    message = message,
                                    section = "global"
                                )

                            messages.add(
                                AiMessage(
                                    role = "assistant",
                                    content = response
                                )
                            )

                            isThinking = false
                        }
                    },
                    enabled = input.isNotBlank() && !isThinking,
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(52.dp)
                ) {
                    Text(
                        text = "↑",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(7.dp)
            )

            Text(
                text = "FRIDAY can make mistakes. Verify important information.",
                color = Color(0xFF4F5966),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: AiMessage
) {
    val isUser = message.role == "user"

    val timeFormat =
        remember {
            SimpleDateFormat(
                "h:mm a",
                Locale.getDefault()
            )
        }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth(
                    if (isUser) 0.86f else 0.92f
                )
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 5.dp,
                        bottomEnd = if (isUser) 5.dp else 18.dp
                    )
                )
                .background(
                    if (isUser) {
                        Color(0xFF1B4F68)
                    } else {
                        Color(0xFF121720)
                    }
                )
                .padding(
                    horizontal = 15.dp,
                    vertical = 12.dp
                )
        ) {

            Text(
                text = if (isUser) "YOU" else "FRIDAY",
                color = if (isUser) {
                    Color(0xFF9BDEFF)
                } else {
                    Color(0xFF5CC8FF)
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = message.content,
                color = Color(0xFFE9EDF2),
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = timeFormat.format(
                    Date(message.createdAt)
                ),
                color = Color(0xFF697483),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun ThinkingBubble() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF121720))
                .padding(
                    horizontal = 15.dp,
                    vertical = 13.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF5CC8FF))
            )

            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Text(
                text = "FRIDAY is thinking...",
                color = Color(0xFF8994A2),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

