package com.health.friday.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.shared.models.JournalEntryDto

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DesktopJournalScreen(
    entries: List<JournalEntryDto> = emptyList(),
    onAddJournalEntry: (String) -> Unit = {}
) {
    var entryText by remember { mutableStateOf("") }

    val allEntries = remember(entries) {
        entries.sortedByDescending { it.timestamp }
    }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // SECTION 1: NEW JOURNAL REFLECTION ENTRY BOX
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Daily Mind Reflection & Journaling", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Journal entries sync through encrypted Google Drive.", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = entryText,
                        onValueChange = { entryText = it },
                        placeholder = { Text("What's on your mind today? (Press Enter to save entry, Shift+Enter for newline)", color = Color(0xFF64748B)) },
                        minLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                    if (keyEvent.isShiftPressed) {
                                        false
                                    } else {
                                        if (entryText.isNotBlank()) {
                                            onAddJournalEntry(entryText.trim())
                                            entryText = ""
                                        }
                                        true
                                    }
                                } else {
                                    false
                                }
                            },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedContainerColor = Color(0xFF0F172A), unfocusedContainerColor = Color(0xFF0F172A))
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (entryText.isNotBlank()) {
                                    onAddJournalEntry(entryText.trim())
                                    entryText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            enabled = entryText.isNotBlank()
                        ) {
                            Text("Save Entry")
                        }

                        Button(
                            onClick = {
                                if (entryText.isNotBlank()) {
                                    onAddJournalEntry(entryText.trim())
                                    entryText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA855F7)),
                            enabled = entryText.isNotBlank()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save with AI Feedback")
                        }
                    }
                }
            }
        }

        // SECTION 2: MEMORY GRAPH CARD
        item {
            Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Journal & Reflection Feed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${allEntries.size} entr${if (allEntries.size == 1) "y" else "ies"} synced", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                }
            }
        }

        // SECTION 3: PAST JOURNAL REFLECTIONS FEED
        item {
            Text("Past Reflections (${allEntries.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (allEntries.isEmpty()) {
            item {
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No journal entries logged yet. Type a reflection above to save.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(allEntries) { entry ->
                Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = dateFormat.format(Date(entry.timestamp)), color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(text = entry.sentiment.name, color = Color(0xFFA855F7), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = entry.content, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}
