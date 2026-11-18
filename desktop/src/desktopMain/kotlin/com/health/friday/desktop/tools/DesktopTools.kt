package com.health.friday.desktop.tools

import com.health.friday.shared.ai.SharedTool
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

class OpenWebPageTool : SharedTool {
    override val name: String = "open_browser"
    override val description: String = "Opens an http/https URL in the desktop default browser. Argument: 'url'"

    override suspend fun execute(params: Map<String, String>): String {
        val url = params["url"] ?: return "Error: 'url' parameter missing"
        val uri = try { URI(url.trim()) } catch (_: Exception) { return "Error: invalid URL" }
        // file:, javascript: and custom schemes are refused.
        if (uri.scheme?.lowercase() !in setOf("http", "https")) {
            return "Refused: only http and https links can be opened"
        }
        return try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
                "Opened $url in the browser"
            } else {
                "Desktop browsing not supported on this platform"
            }
        } catch (e: Exception) {
            "Failed to open browser: ${e.message}"
        }
    }
}

class CopyClipboardTool : SharedTool {
    override val name: String = "copy_clipboard"
    override val description: String = "Copies text to the PC system clipboard. Argument: 'text'"

    override suspend fun execute(params: Map<String, String>): String {
        val text = params["text"] ?: return "Error: 'text' parameter missing"
        return try {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
            "Text copied to system clipboard"
        } catch (e: Exception) {
            "Failed to copy to clipboard: ${e.message}"
        }
    }
}
// RunSystemCommandTool deleted: it executed arbitrary text as a command.
