package com.health.friday.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.health.friday.ai.AiMessage
import com.health.friday.ai.AiOrchestrator
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen(
    orchestrator: AiOrchestrator,
    modifier: Modifier = Modifier
) {

    val messages =
        remember {
            mutableStateListOf(
                AiMessage(
                    role = "assistant",
                    content = "FRIDAY online. What do you need?"
                )
            )
        }

    var input by remember {
        mutableStateOf("")
    }

    var isThinking by remember {
        mutableStateOf(false)
    }

    val scope =
        rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "FRIDAY",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Your personal assistant",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                top = 4.dp,
                bottom = 16.dp
            )
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            items(messages) { message ->

                Text(
                    text =
                        if (message.role == "user") {
                            "You: ${message.content}"
                        } else {
                            "FRIDAY: ${message.content}"
                        },
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (isThinking) {

                item {

                    Text(
                        text = "FRIDAY is thinking..."
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Ask FRIDAY anything...")
                },
                enabled = !isThinking,
                maxLines = 4
            )

            Button(
                onClick = {

                    val message =
                        input.trim()

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
                enabled =
                    input.isNotBlank() &&
                            !isThinking
            ) {
                Text("Send")
            }
        }
    }
}