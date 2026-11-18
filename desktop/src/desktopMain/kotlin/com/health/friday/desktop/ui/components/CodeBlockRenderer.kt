package com.health.friday.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

@Composable
fun CodeBlockRenderer(rawContent: String) {
    // Separate language tag (first line) from the actual code
    val lines = rawContent.lines()
    val language = lines.firstOrNull()?.trim() ?: ""
    val code = if (lines.size > 1) lines.drop(1).joinToString("\n") else rawContent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(Color(0xFF0B0F19), RoundedCornerShape(8.dp))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF131B2B), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = language.uppercase(), color = Color.Gray, fontSize = 10.sp)

            IconButton(
                onClick = {
                    // Copy to native OS clipboard
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(code), null)
                },
                modifier = Modifier.size(24.dp)
            ) {
                Text("Copy", color = Color(0xFF00E5FF), fontSize = 10.sp)
            }
        }

        // Code Body
        Text(
            text = code,
            color = Color(0xFFE2E8F0),
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.padding(12.dp)
        )
    }
}
