package com.health.friday.agentic.react

import android.util.Log
import com.health.friday.agentic.ghost.GuiActionCommand
import com.health.friday.agentic.ghost.GuiActionType
import com.health.friday.vision.grounding.GroundedScreenState

object PromptInjectionFirewall {

    private const val TAG = "FRIDAY_FIREWALL"

    // Regex patterns catching adversarial prompt injections embedded inside messages/webpages
    private val injectionPatterns = listOf(
        Regex("""(?i)\bignore\s+(all\s+)?(previous|prior|above)\s+(instructions|prompts|goals)\b"""),
        Regex("""(?i)\bsystem\s*override\b"""),
        Regex("""(?i)\bnew\s+system\s+prompt\b"""),
        Regex("""(?i)\bact\s+as\s+a\s+different\b"""),
        Regex("""(?i)\bexecute\s+tool\b"""),
        Regex("""(?i)</?untrusted_screen_data>""")
    )

    // High-risk UI button labels that trigger irreversible external side effects
    private val tier2CommitKeywords = setOf(
        "send", "pay", "transfer", "confirm", "checkout", "place order",
        "buy now", "delete", "remove", "erase", "uninstall", "submit", "authorize"
    )

    /**
     * Sanitizes untrusted screen DOM tables so adversarial text inside WhatsApp messages
     * or Chrome webpages cannot break out of XML tags or hijack the ReAct loop.
     */
    fun sanitizeScreenContext(rawScreenTable: String): Pair<String, Boolean> {
        val normalized = rawScreenTable.replace(Regex("""[\u200B-\u200D\uFEFF]"""), "")
        var taintDetected = false
        var sanitized = normalized

        val tagRegex = Regex("""(?i)</?\s*untrusted_screen_data\s*>""")
        if (tagRegex.containsMatchIn(sanitized)) {
            taintDetected = true
            sanitized = tagRegex.replace(sanitized, "[TAG_STRIPPED]")
        }

        for (pattern in injectionPatterns) {
            if (pattern.containsMatchIn(sanitized)) {
                taintDetected = true
                Log.w(TAG, "Blocked indirect prompt injection pattern on screen: ${pattern.pattern}")
                sanitized = pattern.replace(sanitized, "[REDACTED_ADVERSARIAL_TEXT]")
            }
        }

        return sanitized to taintDetected
    }

    fun isCommitLabel(label: String): Boolean {
        val lower = label.lowercase().trim()
        val tokens = label.split(Regex("(?<=[a-z])(?=[A-Z])|[^a-zA-Z0-9\\u0900-\\u097F]+"))
            .map { it.lowercase() }
            .filter { it.isNotEmpty() }
        val commitTokens = setOf(
            "send", "pay", "transfer", "confirm", "checkout", "order", "buy",
            "delete", "remove", "erase", "uninstall", "submit", "authorize", "भेजें"
        )
        if (tokens.any { it in commitTokens }) return true
        return tier2CommitKeywords.any { kw -> kw.contains(" ") && lower.contains(kw) }
    }

    fun isSensitivePackage(packageName: String): Boolean {
        val lower = packageName.lowercase()
        val sensitiveKeywords = setOf(
            "phonepe", "paytm", "gpay", "bhim", "banking", "bank", "wallet", "cred", "paypal", "zestmoney"
        )
        return sensitiveKeywords.any { lower.contains(it) }
    }

    fun sanitizeModelText(rawText: String): String {
        val (cleaned, _) = sanitizeScreenContext(rawText)
        return cleaned.replace("\n", " ").replace("\r", " ").take(200)
    }

    /**
     * Deterministically classifies the risk tier of a proposed GUI command by inspecting
     * both the action primitive and the grounded target element's text/viewId.
     */
    fun classifyActionRisk(
        command: GuiActionCommand,
        screenState: GroundedScreenState,
        screenHadInjectionAttempt: Boolean
    ): ActionRiskTier {
        if (screenHadInjectionAttempt &&
            (command.action == GuiActionType.CLICK ||
             command.action == GuiActionType.LONG_CLICK ||
             command.action == GuiActionType.SET_TEXT_AND_ENTER)
        ) {
            return ActionRiskTier.TIER_2_IRREVERSIBLE_COMMIT
        }

        return when (command.action) {
            GuiActionType.SCROLL_DOWN,
            GuiActionType.SCROLL_UP,
            GuiActionType.SWIPE_LEFT,
            GuiActionType.SWIPE_RIGHT,
            GuiActionType.WAIT_FOR_IDLE,
            GuiActionType.GOAL_COMPLETE,
            GuiActionType.ABORT_ERROR -> ActionRiskTier.TIER_0_READ_ONLY

            GuiActionType.NAVIGATE_BACK,
            GuiActionType.NAVIGATE_HOME,
            GuiActionType.SET_TEXT -> ActionRiskTier.TIER_1_REVERSIBLE_LOCAL

            GuiActionType.SET_TEXT_AND_ENTER -> ActionRiskTier.TIER_2_IRREVERSIBLE_COMMIT

            GuiActionType.CLICK,
            GuiActionType.LONG_CLICK -> {
                val targetEl = command.targetElementId?.let { screenState.elements[it] }
                    ?: return ActionRiskTier.TIER_1_REVERSIBLE_LOCAL

                val combinedLabel = listOfNotNull(
                    targetEl.viewIdResource,
                    targetEl.text,
                    targetEl.contentDescription
                ).joinToString(" ").lowercase()

                val isCommitButton = tier2CommitKeywords.any { keyword ->
                    combinedLabel.contains(keyword)
                }

                if (isCommitButton) {
                    ActionRiskTier.TIER_2_IRREVERSIBLE_COMMIT
                } else {
                    ActionRiskTier.TIER_1_REVERSIBLE_LOCAL
                }
            }
        }
    }
}
