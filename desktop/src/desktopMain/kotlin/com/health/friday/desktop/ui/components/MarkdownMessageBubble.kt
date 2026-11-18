package com.health.friday.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.shared.models.ChatMessageDto
import com.health.friday.shared.models.ChatRole

@Composable
fun MarkdownMessageBubble(message: ChatMessageDto) {
    val isUser = message.sender == ChatRole.USER
    val bubbleColor = if (isUser) Color(0x3300E5FF) else Color(0xFF1E293B)
    val alignment = if (isUser) Arrangement.End else Arrangement.Start
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 640.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isUser) "You" else "F.R.I.D.A.Y. (Local AI)",
                        color = if (isUser) Color(0xFFE0F2FE) else Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    IconButton(
                        onClick = { clipboardManager.setText(AnnotatedString(message.text)) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy text",
                            tint = if (isUser) Color(0xFFBAE6FD) else Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                val chunks = message.text.split("```")

                SelectionContainer {
                    Column {
                        chunks.forEachIndexed { index, content ->
                            if (content.isBlank()) return@forEachIndexed

                            if (index % 2 == 0) {
                                // Inject Animated Streaming Text for normal prose
                                AnimatedStreamingText(
                                    fullText = content.trim(),
                                    isStreaming = message.isStreaming && (index == chunks.lastIndex)
                                )
                            } else {
                                // Inject Skia scanning code block
                                val lines = content.lines()
                                val language = lines.firstOrNull()?.trim() ?: ""
                                val codeBody = if (lines.size > 1) lines.drop(1).joinToString("\n") else content

                                AnimatedCodeBlock(code = codeBody, language = language)
                            }
                        }
                    }
                }
            }
        }
    }
}
