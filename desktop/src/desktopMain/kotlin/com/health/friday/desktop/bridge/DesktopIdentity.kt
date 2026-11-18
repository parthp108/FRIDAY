package com.health.friday.desktop.bridge

import com.health.friday.shared.protocol.Ids
import java.io.File

/** Stable per-install identity (~/.friday/device-id), used as the sync node id. */
object DesktopIdentity {
    val deviceId: String by lazy { loadOrCreate() }

    val deviceName: String =
        System.getenv("COMPUTERNAME") ?: System.getenv("HOSTNAME") ?: "Desktop"

    private fun loadOrCreate(): String {
        val dir = File(System.getProperty("user.home"), ".friday").apply { mkdirs() }
        val file = File(dir, "device-id")
        val existing = runCatching { file.readText().trim() }.getOrNull()
        if (!existing.isNullOrEmpty()) return existing
        val fresh = Ids.newDeviceId()
        runCatching { file.writeText(fresh) }
        return fresh
    }
}
