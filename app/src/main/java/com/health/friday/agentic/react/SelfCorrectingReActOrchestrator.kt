package com.health.friday.agentic.react

import android.graphics.Bitmap
import android.util.Log
import com.health.friday.agentic.ghost.GhostFingerExecutor
import com.health.friday.agentic.ghost.GuiActionCommand
import com.health.friday.agentic.ghost.GuiActionType
import com.health.friday.os.bridge.FridayAccessibilityService
import com.health.friday.data.local.ActionLogDao
import com.health.friday.data.local.ActionLogEntity
import com.health.friday.inference.NpuInferenceEngine
import com.health.friday.vision.grounding.GroundedScreenState
import com.health.friday.vision.grounding.HybridScreenGrounder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class SelfCorrectingReActOrchestrator(
    private val accessibilityService: FridayAccessibilityService,
    private val screenGrounder: HybridScreenGrounder,
    private val npuEngine: NpuInferenceEngine,
    private val actionLogDao: ActionLogDao,
    private val ghostFinger: GhostFingerExecutor = GhostFingerExecutor(accessibilityService),
    private val hitlOverlay: AccessibilityHitlOverlay = AccessibilityHitlOverlay(accessibilityService)
) {
    companion object {
        private const val TAG = "FRIDAY_REACT_ENGINE"
        private const val STAGNATION_JACCARD_THRESHOLD = 0.04
    }

    /**
     * Executes a multi-step goal with automatic self-correction, Tabu action masking,
     * 2-cycle oscillation detection, and Tier-2 Human-in-the-Loop security gating.
     */
    suspend fun executeReActGoal(
        trustedUserGoal: String,
        requireHitlOnTier2: Boolean = true,
        maxSteps: Int = 10,
        onThoughtStream: (ReActStepRecord) -> Unit = {}
    ): String {
        val trajectory = mutableListOf<ReActStepRecord>()
        val stateHistory = mutableListOf<GroundedScreenState>()
        val tabuActions = LinkedHashSet<TabuActionKey>()

        var forceVisualSoMNextStep = false
        var activeReflectionCritique: String? = null

        for (step in 1..maxSteps) {
            val currentState = screenGrounder.groundScreen(
                rootNode = accessibilityService.rootInActiveWindow,
                screenshotProvider = { accessibilityService.captureScreenBitmap() },
                forceVisualSoM = forceVisualSoMNextStep
            )
            stateHistory.add(currentState)
            val currentStructHash = DomSimilarityMath.computeStructuralHash(currentState)

            val (sanitizedTable, hadInjectionAttempt) = PromptInjectionFirewall.sanitizeScreenContext(
                currentState.promptContextTable
            )

            val maskedForCurrentState = tabuActions
                .filter { it.structuralHash == currentStructHash }
                .map { "${it.action}(id=${it.targetElementId})" }

            val proposedCommand = queryNpuForReActStep(
                trustedUserGoal = trustedUserGoal,
                sanitizedScreenTable = sanitizedTable,
                annotatedBitmap = currentState.annotatedSomBitmap,
                trajectory = trajectory,
                maskedActions = maskedForCurrentState,
                reflectionCritique = activeReflectionCritique
            )

            if (proposedCommand.action == GuiActionType.GOAL_COMPLETE) {
                return "Goal achieved in ${step - 1} steps: ${proposedCommand.thought}"
            }
            if (proposedCommand.action == GuiActionType.ABORT_ERROR) {
                return "Agent aborted safely: ${proposedCommand.thought}"
            }

            val candidateKey = TabuActionKey(
                structuralHash = currentStructHash,
                action = proposedCommand.action,
                targetElementId = proposedCommand.targetElementId
            )
            val effectiveCommand = if (tabuActions.contains(candidateKey)) {
                Log.w(TAG, "Model attempted Tabu action $candidateKey; forcing SCROLL_DOWN recovery.")
                GuiActionCommand(
                    thought = "Overriding repeated failed action on [${proposedCommand.targetElementId}] with SCROLL_DOWN.",
                    action = GuiActionType.SCROLL_DOWN
                )
            } else {
                proposedCommand
            }

            val riskTier = PromptInjectionFirewall.classifyActionRisk(
                command = effectiveCommand,
                screenState = currentState,
                screenHadInjectionAttempt = hadInjectionAttempt
            )

            if (riskTier == ActionRiskTier.TIER_2_IRREVERSIBLE_COMMIT && requireHitlOnTier2) {
                val targetEl = effectiveCommand.targetElementId?.let { currentState.elements[it] }
                val targetLabel = targetEl?.text ?: targetEl?.contentDescription ?: targetEl?.viewIdResource ?: "Element [${effectiveCommand.targetElementId}]"

                val approved = hitlOverlay.requestUserConfirmation(
                    actionSummary = effectiveCommand.thought,
                    targetLabel = targetLabel
                )
                if (!approved) {
                    return "Execution halted by user at Tier-2 Security Gate before clicking '$targetLabel'."
                }
            }

            val dispatched = ghostFinger.executeCommand(effectiveCommand, currentState)
            delay(180L)

            val postState = screenGrounder.groundScreen(
                rootNode = accessibilityService.rootInActiveWindow,
                screenshotProvider = { null },
                forceVisualSoM = false
            )
            val jaccardDelta = DomSimilarityMath.computeJaccardDistance(currentState, postState)

            val isTwoCycleOscillation = stateHistory.size >= 2 &&
                    DomSimilarityMath.computeJaccardDistance(stateHistory[stateHistory.size - 2], postState) < STAGNATION_JACCARD_THRESHOLD

            val isStagnant = !dispatched ||
                    (jaccardDelta < STAGNATION_JACCARD_THRESHOLD && effectiveCommand.action != GuiActionType.SET_TEXT)

            if (isStagnant || isTwoCycleOscillation) {
                tabuActions.add(candidateKey)
                forceVisualSoMNextStep = true

                activeReflectionCritique = if (isTwoCycleOscillation) {
                    "[REFLEXION CRITIQUE]: 2-Cycle UI oscillation detected! You returned to the exact previous screen. Do NOT click [${effectiveCommand.targetElementId}] again."
                } else {
                    "[REFLEXION CRITIQUE]: Action ${effectiveCommand.action} on element [${effectiveCommand.targetElementId}] produced Δ_J=${"%.3f".format(jaccardDelta)} (screen did not change). Element [${effectiveCommand.targetElementId}] is now MASKED. Try a different element ID or scroll."
                }
                Log.w(TAG, activeReflectionCritique)
            } else {
                forceVisualSoMNextStep = false
                activeReflectionCritique = null
            }

            val stepRecord = ReActStepRecord(
                stepNumber = step,
                observationSummary = "Pkg=${currentState.packageName}, Elements=${currentState.elements.size}",
                thought = effectiveCommand.thought,
                command = effectiveCommand,
                riskTier = riskTier,
                jaccardDistance = jaccardDelta,
                reflectionCritique = activeReflectionCritique
            )
            trajectory.add(stepRecord)
            onThoughtStream(stepRecord)

            actionLogDao.insertAction(
                ActionLogEntity(
                    timestamp = System.currentTimeMillis(),
                    toolName = "react_step_${effectiveCommand.action.name.lowercase()}",
                    description = "Step $step [${riskTier.name}]: ${effectiveCommand.thought} (Δ_J=${"%.2f".format(jaccardDelta)})",
                    inverseTool = "navigate_back",
                    inversePayloadJson = JSONObject().put("package", currentState.packageName).toString()
                )
            )
        }

        return "Reached maximum ReAct horizon ($maxSteps steps) for goal: $trustedUserGoal"
    }

    private suspend fun queryNpuForReActStep(
        trustedUserGoal: String,
        sanitizedScreenTable: String,
        annotatedBitmap: Bitmap?,
        trajectory: List<ReActStepRecord>,
        maskedActions: List<String>,
        reflectionCritique: String?
    ): GuiActionCommand = suspendCancellableCoroutine { cont ->

        val historyText = if (trajectory.isEmpty()) {
            "None"
        } else {
            trajectory.takeLast(4).joinToString("\n") { rec ->
                "Step ${rec.stepNumber}: Thought='${rec.thought}' -> Action=${rec.command.action}(id=${rec.command.targetElementId}) -> Δ_J=${"%.2f".format(rec.jaccardDistance)}"
            }
        }

        val prompt = buildString {
            appendLine("You are FRIDAY's Self-Correcting ReAct OS Agent.")
            appendLine("TRUSTED_USER_GOAL: $trustedUserGoal")
            appendLine("RECENT_TRAJECTORY:")
            appendLine(historyText)
            if (maskedActions.isNotEmpty()) {
                appendLine("FORBIDDEN_TABU_ACTIONS_ON_THIS_SCREEN: ${maskedActions.joinToString(", ")}")
            }
            if (!reflectionCritique.isNullOrBlank()) {
                appendLine(reflectionCritique)
            }
            appendLine("<untrusted_screen_data>")
            appendLine(sanitizedScreenTable)
            appendLine("</untrusted_screen_data>")
            appendLine("""
                Respond with strictly ONE JSON object matching this exact schema:
                {"thought": "concise observation + plan", "action": "CLICK|LONG_CLICK|SET_TEXT|SET_TEXT_AND_ENTER|SCROLL_DOWN|SCROLL_UP|NAVIGATE_BACK|GOAL_COMPLETE|ABORT_ERROR", "target_id": 1, "text": null}
            """.trimIndent())
        }

        CoroutineScope(Dispatchers.IO).launch {
            npuEngine.generateFastResponse(
                userPrompt = prompt,
                screenBitmap = annotatedBitmap,
                onToken = {},
                onComplete = { rawOutput, _, _ ->
                    if (cont.isActive) {
                        cont.resume(parseStrictJsonWithRepair(rawOutput))
                    }
                },
                onError = { err ->
                    if (cont.isActive) {
                        cont.resume(
                            GuiActionCommand(
                                thought = "NPU inference error: $err",
                                action = GuiActionType.ABORT_ERROR
                            )
                        )
                    }
                }
            )
        }
    }

    private fun parseStrictJsonWithRepair(raw: String): GuiActionCommand {
        val startIdx = raw.indexOf('{')
        if (startIdx == -1) {
            return GuiActionCommand(
                thought = "Malformed output (no JSON object): ${raw.take(60)}",
                action = GuiActionType.ABORT_ERROR
            )
        }

        var candidate = raw.substring(startIdx).trim()
        val endIdx = candidate.lastIndexOf('}')
        candidate = if (endIdx != -1) {
            candidate.substring(0, endIdx + 1)
        } else {
            "$candidate\"}"
        }

        return try {
            val json = JSONObject(candidate)
            val thought = json.optString("thought", "Executing next step")
            val actionRaw = json.optString("action", "ABORT_ERROR").trim().uppercase()
            val action = runCatching { GuiActionType.valueOf(actionRaw) }
                .getOrDefault(GuiActionType.ABORT_ERROR)

            val targetId = if (json.has("target_id") && !json.isNull("target_id")) {
                json.optInt("target_id")
            } else null

            val text = if (json.has("text") && !json.isNull("text")) {
                json.optString("text")
            } else null

            GuiActionCommand(
                thought = thought,
                action = action,
                targetElementId = targetId,
                textInput = text
            )
        } catch (e: Exception) {
            GuiActionCommand(
                thought = "JSON parse recovery failed: ${e.message}",
                action = GuiActionType.ABORT_ERROR
            )
        }
    }
}
