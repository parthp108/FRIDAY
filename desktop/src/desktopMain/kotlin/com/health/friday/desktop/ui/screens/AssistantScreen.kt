package com.health.friday.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.desktop.ui.components.MarkdownMessageBubble
import com.health.friday.shared.models.ChatMessageDto
import com.health.friday.shared.models.ChatSessionDto

fun parseMarkdownToAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        val boldRegex = Regex("""\*\*(.*?)\*\*""")
        var lastIndex = 0
        for (match in boldRegex.findAll(text)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start > lastIndex) {
                append(text.substring(lastIndex, start))
            }
            withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }
            lastIndex = end
        }
        if (lastIndex < text.length) {
            append(text.substring(lastIndex))
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Current"
    val diffMs = System.currentTimeMillis() - timestamp
    val mins = diffMs / (1000 * 60)
    val hours = mins / 60
    val days = hours / 24

    return when {
        mins < 2 -> "Just now"
        mins < 60 -> "${mins}m"
        hours < 24 -> "${hours}h"
        else -> "${days}d"
    }
}

@Composable
fun DesktopAssistantScreen(
    messages: List<ChatMessageDto>,
    sessions: List<ChatSessionDto> = emptyList(),
    currentSessionId: String = "",
    onSendMessage: (String) -> Unit,
    onNewChat: () -> Unit = {},
    onSelectSession: (String) -> Unit = {},
    onDeleteSession: (String) -> Unit = {},
    onClearHistory: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    var isDropdownOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val currentSession = sessions.firstOrNull { it.id == currentSessionId }
    val currentTitle = currentSession?.title ?: "Current Chat"

    // Auto-scroll to latest message during streaming
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // TOP CONTROL BAR WITH RECENT CHATS DROPDOWN & NEW CHAT BUTTON
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // NEW CHAT BUTTON
                Button(
                    onClick = onNewChat,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // RECENT CHATS DROPDOWN CONTROL
                Box {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { isDropdownOpen = !isDropdownOpen }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Forum, contentDescription = "Recent Chats", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentTitle,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 240.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                        }
                    }

                    // RECENT CHATS DROPDOWN MENU
                    DropdownMenu(
                        expanded = isDropdownOpen,
                        onDismissRequest = { isDropdownOpen = false },
                        modifier = Modifier.width(360.dp).background(Color(0xFF0F172A))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Recent Chats (${sessions.size})",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                            )

                            if (sessions.isEmpty()) {
                                Text("No recent chats", color = Color(0xFF64748B), fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    sessions.forEach { s ->
                                        val isCurrent = s.id == currentSessionId
                                        Surface(
                                            color = if (isCurrent) Color(0xFF1E293B) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    onSelectSession(s.id)
                                                    isDropdownOpen = false
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    Icon(
                                                        imageVector = Icons.Default.SmartToy,
                                                        contentDescription = null,
                                                        tint = if (isCurrent) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = s.title.ifBlank { "Untitled Chat" },
                                                        color = if (isCurrent) Color.White else Color(0xFFCBD5E1),
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (isCurrent) {
                                                        Surface(color = Color(0xFF166534), shape = RoundedCornerShape(4.dp)) {
                                                            Text("Current", color = Color(0xFF86EFAC), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                        }
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                    } else {
                                                        Text(formatRelativeTime(s.timestamp), color = Color(0xFF64748B), fontSize = 11.sp)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                    }

                                                    IconButton(
                                                        onClick = { onDeleteSession(s.id) },
                                                        modifier = Modifier.size(20.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Delete session", tint = Color(0xFFF87171), modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // CLEAR ALL BUTTON
            TextButton(
                onClick = onClearHistory,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF87171))
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clear All Chats", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // CHAT MESSAGES FEED
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                MarkdownMessageBubble(msg)
            }
        }

        // INPUT FIELD
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Ask FRIDAY or type command... (Enter to send, Shift+Enter for newline)", color = Color(0xFF64748B)) },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                            if (keyEvent.isShiftPressed) {
                                false
                            } else {
                                if (inputText.isNotBlank()) {
                                    onSendMessage(inputText)
                                    inputText = ""
                                }
                                true
                            }
                        } else {
                            false
                        }
                    },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF1E293B), unfocusedContainerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                })
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier.size(48.dp).background(Color(0xFF0284C7), RoundedCornerShape(12.dp))
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send", tint = Color.White)
            }
        }
    }
}
