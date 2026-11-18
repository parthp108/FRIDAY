package com.health.friday.proactive

import android.util.Log

class EmotionalReactionEngine {

    companion object {
        private const val TAG = "FRIDAY_EMOTION"

        private val DISMISSIVE_KEYWORDS = setOf(
            "not now", "stop", "busy", "shut up", "go away", "leave me alone",
            "later", "shut it", "quiet", "don't bother me", "no thanks", "shh",
            "cancel", "nevermind", "never mind", "ignore", "don't care", "no",
            "nope", "screw off", "get lost"
        )
    }

    sealed class ReactionResult {
        data class Dismissed(
            val reason: String,
            val apologyText: String = "Understood, boss. Leaving you to it!"
        ) : ReactionResult()

        data class Engaged(
            val userText: String
        ) : ReactionResult()
    }

    /**
     * Evaluates the recognized user speech to detect if the user sounds annoyed/busy
     * or wants to engage in the proactive task flow.
     */
    fun evaluateReaction(spokenText: String): ReactionResult {
        val clean = spokenText.trim().lowercase()

        if (clean.isBlank()) {
            return ReactionResult.Dismissed(
                reason = "Silence / No response",
                apologyText = ""
            )
        }

        // Check if user uttered a dismissive or annoyed phrase
        val isDismissive = DISMISSIVE_KEYWORDS.any { keyword ->
            clean.contains(keyword)
        }

        if (isDismissive) {
            Log.i(TAG, "User expressed dismissive/annoyed reaction: '$spokenText'")
            return ReactionResult.Dismissed(
                reason = "User requested interruption stop",
                apologyText = "Understood, boss. Leaving you to it!"
            )
        }

        Log.i(TAG, "User engaged proactively: '$spokenText'")
        return ReactionResult.Engaged(userText = spokenText)
    }
}
