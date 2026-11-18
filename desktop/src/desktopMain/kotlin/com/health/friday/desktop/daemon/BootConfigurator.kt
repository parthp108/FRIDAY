package com.health.friday.desktop.daemon

import java.io.File

object BootConfigurator {
    fun ensureRunOnStartup() {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            val userHome = System.getProperty("user.home")
            val startupFolder = File("$userHome\\AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Startup")
            val shortcutFile = File(startupFolder, "FRIDAY.lnk")

            if (!shortcutFile.exists()) {
                val installPath = System.getProperty("jpackage.app-path") ?: return
                val psCommand = """
                    ${'$'}s=(New-Object -COM WScript.Shell).CreateShortcut('${shortcutFile.absolutePath}');
                    ${'$'}s.TargetPath='$installPath';
                    ${'$'}s.Save()
                """.trimIndent()

                runCatching {
                    Runtime.getRuntime().exec(arrayOf("powershell.exe", "-Command", psCommand))
                }
            }
        }
    }
}
