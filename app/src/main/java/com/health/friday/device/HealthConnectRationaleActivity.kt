package com.health.friday.device

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class HealthConnectRationaleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text =
                "FRIDAY uses Health Connect to read your health data, including steps, heart rate, and sleep. This data is used only to provide health information and features inside FRIDAY."

            textSize = 16f
            setPadding(48, 48, 48, 48)
        }

        setContentView(textView)
    }
}