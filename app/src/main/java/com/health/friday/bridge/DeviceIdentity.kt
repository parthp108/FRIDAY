package com.health.friday.bridge

import android.content.Context
import android.os.Build
import com.health.friday.shared.protocol.Ids

/** Stable per-install identity, used as the sync node id. */
class DeviceIdentity(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("friday_device", Context.MODE_PRIVATE)

    val deviceId: String = prefs.getString("device_id", null)
        ?: Ids.newDeviceId().also { prefs.edit().putString("device_id", it).apply() }

    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
}
