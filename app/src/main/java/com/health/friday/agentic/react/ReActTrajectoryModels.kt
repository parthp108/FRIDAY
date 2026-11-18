package com.health.friday.agentic.react

import com.health.friday.agentic.ghost.GuiActionCommand
import com.health.friday.agentic.ghost.GuiActionType
import com.health.friday.vision.grounding.GroundedScreenState
import com.health.friday.vision.grounding.GroundedUiElement

enum class SecurityLatticeLevel {
    TRUSTED_USER,
    UNTRUSTED_SCREEN
}

enum class ActionRiskTier {
    TIER_0_READ_ONLY,         // Scroll, wait, inspect DOM -> Auto-execute
    TIER_1_REVERSIBLE_LOCAL,  // Draft text into input field, back nav -> Auto-execute + log
    TIER_2_IRREVERSIBLE_COMMIT // Send message, payment, delete, checkout -> Requires HITL Gate
}

data class TabuActionKey(
    val structuralHash: String,
    val action: GuiActionType,
    val targetElementId: Int?
)

data class ReActStepRecord(
    val stepNumber: Int,
    val observationSummary: String,
    val thought: String,
    val command: GuiActionCommand,
    val riskTier: ActionRiskTier,
    val jaccardDistance: Double,
    val reflectionCritique: String?
)

object DomSimilarityMath {

    /**
     * Extracts clock-tick-invariant structural signatures for every element on screen.
     * Quantizes coordinates to 16px grid cells to ignore 1-2px layout jitter.
     */
    fun extractStructuralSignatures(state: GroundedScreenState): Set<String> {
        val signatures = LinkedHashSet<String>(state.elements.size)
        for (el in state.elements.values) {
            val gridX = el.boundsInScreen.centerX() / 16
            val gridY = el.boundsInScreen.centerY() / 16
            val sig = "${el.packageNameOrClass()}:${el.viewIdResource ?: "noid"}:" +
                    "${el.isClickable}:${el.isEditable}:$gridX:$gridY"
            signatures.add(sig)
        }
        return signatures
    }

    private fun GroundedUiElement.packageNameOrClass(): String {
        return className ?: "View"
    }

    /**
     * Computes Jaccard Structural Distance Δ_J(S_{t-1}, S_t) in [0.0, 1.0].
     * 0.0 = identical screen structure; 1.0 = completely different screen.
     */
    fun computeJaccardDistance(
        prevState: GroundedScreenState,
        currState: GroundedScreenState
    ): Double {
        if (prevState.packageName != currState.packageName) return 1.0

        val setA = extractStructuralSignatures(prevState)
        val setB = extractStructuralSignatures(currState)

        if (setA.isEmpty() && setB.isEmpty()) return 0.0
        val intersectionSize = setA.intersect(setB).size.toDouble()
        val unionSize = setA.union(setB).size.toDouble()

        return if (unionSize == 0.0) 0.0 else 1.0 - (intersectionSize / unionSize)
    }

    /**
     * Computes a compact structural hash invariant to clock ticks or dynamic text changes.
     */
    fun computeStructuralHash(state: GroundedScreenState): String {
        val joined = extractStructuralSignatures(state).joinToString("|")
        return joined.hashCode().toUInt().toString(16)
    }
}
