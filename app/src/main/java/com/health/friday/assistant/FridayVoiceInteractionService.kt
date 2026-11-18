package com.health.friday.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import com.health.friday.MainActivity

class FridayVoiceInteractionService : VoiceInteractionService() {

    companion object {
        @Volatile
        private var instance: FridayVoiceInteractionService? = null

        fun triggerAmbientSession(initialQuery: String? = null) {
            val service = instance ?: return
            val args = Bundle().apply {
                if (initialQuery != null) {
                    putString("EXTRA_INITIAL_QUERY", initialQuery)
                }
                putString("EXTRA_TRIGGER_SOURCE", "WAKEWORD")
            }
            service.showSession(
                args,
                VoiceInteractionSession.SHOW_WITH_ASSIST
            )
        }
    }

    override fun onReady() {
        super.onReady()
        instance = this
    }

    override fun onLaunchVoiceAssistFromKeyguard() {
        super.onLaunchVoiceAssistFromKeyguard()
        val args = Bundle().apply {
            putBoolean("EXTRA_FROM_KEYGUARD", true)
        }
        showSession(args, 0)
    }

    override fun onShutdown() {
        instance = null
        super.onShutdown()
    }
}
