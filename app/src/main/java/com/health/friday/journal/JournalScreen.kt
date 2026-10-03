
package com.health.friday.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.data.local.JournalEntry
import com.health.friday.ui.components.ScreenHeader
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCardLight
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun JournalScreen(
    repository: JournalRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSendWithFeedback: (suspend (String) -> Unit)? = null
) {

    val scope = rememberCoroutineScope()

    val entriesFlow = remember(repository) {
        repository.getEntries()
    }

    val entries by entriesFlow.collectAsState(initial = emptyList())

    var text by remember {
        mutableStateOf("")
    }

    var feedbackBusy by remember {
        mutableStateOf(false)
    }

    var feedbackError by remember {
        mutableStateOf<String?>(null)
    }

    val dateFormat = remember {
        SimpleDateFormat(
            "dd MMM yyyy, HH:mm",
            Locale.getDefault()
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FridayBackground),
        contentPadding = PaddingValues(
            start = 18.dp,
            top = 8.dp,
            end = 18.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack
                ) {
                    Text(
                        text = "‹ Back",
                        color = FridayCyan
                    )
                }
            }
        }

        item {
            ScreenHeader(
                title = "Journal",
                subtitle = "Send saves it on your phone only."
            )
        }

        item {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    feedbackError = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("What's on your mind?")
                },
                minLines = 6,
                enabled = !feedbackBusy
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {

                Button(
                    onClick = {
                        val entryText = text
                        text = ""

                        scope.launch {
                            repository.saveEntry(entryText)
                        }
                    },
                    enabled =
                        text.isNotBlank() &&
                                !feedbackBusy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FridayCyan,
                        contentColor = FridayBackground
                    )
                ) {
                    Text("Send")
                }

                OutlinedButton(
                    onClick = {

                        val entryText = text.trim()

                        scope.launch {

                            feedbackBusy = true
                            feedbackError = null

                            try {

                                onSendWithFeedback?.invoke(entryText)

                                text = ""

                            } catch (e: Exception) {

                                feedbackError =
                                    e.message
                                        ?: "Could not generate feedback."

                            } finally {

                                feedbackBusy = false
                            }
                        }
                    },
                    enabled =
                        onSendWithFeedback != null &&
                                text.isNotBlank() &&
                                !feedbackBusy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FridayCyan,
                        disabledContentColor = FridayMuted
                    )
                ) {
                    Text(
                        if (feedbackBusy) {
                            "Thinking..."
                        } else {
                            "Send with feedback"
                        }
                    )
                }
            }
        }

        if (feedbackError != null) {
            item {
                Text(
                    text = feedbackError!!,
                    color = FridayMuted,
                    fontSize = 12.sp
                )
            }
        }

        if (onSendWithFeedback == null) {
            item {
                Text(
                    text = "Feedback needs the AI connected. Not built yet.",
                    color = FridayMuted,
                    fontSize = 12.sp
                )
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = "No entries yet.",
                    color = FridayMuted,
                    fontSize = 14.sp
                )
            }
        }

        items(
            items = entries,
            key = { it.id }
        ) { entry ->

            JournalEntryCard(
                entry = entry,
                dateText = dateFormat.format(Date(entry.createdAt)),
                onDelete = {
                    scope.launch {
                        repository.deleteEntry(entry)
                    }
                }
            )
        }
    }
}

@Composable
private fun JournalEntryCard(
    entry: JournalEntry,
    dateText: String,
    onDelete: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = FridayCard
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = dateText,
                    color = FridayMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
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

            Text(
                text = entry.text,
                color = FridayText,
                fontSize = 15.sp,
                modifier = Modifier.padding(end = 8.dp)
            )

            if (entry.feedback != null) {

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = FridayCardLight
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {

                        Text(
                            text = "FRIDAY",
                            color = FridayCyan,
                            fontSize = 12.sp
                        )

                        Text(
                            text = entry.feedback,
                            color = FridayText,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

