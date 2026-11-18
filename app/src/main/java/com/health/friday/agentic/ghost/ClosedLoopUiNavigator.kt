package com.health.friday.agentic.ghost

import android.util.Log
import com.health.friday.inference.NpuInferenceEngine
import com.health.friday.os.bridge.FridayAccessibilityService
import com.health.friday.vision.grounding.HybridScreenGrounder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class ClosedLoopUiNavigator(
    private val accessibilityService: FridayAccessibilityService,
    private val screenGrounder: HybridScreenGrounder,
    private val npuEngine: NpuInferenceEngine,
    private val ghostFinger: GhostFingerExecutor = GhostFingerExecutor(accessibilityService)
) {
    companion object {
        private const val TAG = "FRIDAY_UI_NAVIGATOR"
    }

    /**
     * Runs a closed-loop GUI navigation session until [userGoal] is achieved
     * or [maxSteps] is reached.
     */
    suspend fun executeGoalOnScreen(
        userGoal: String,
        fastCommitSendButton: Boolean = false,
        maxSteps: Int = 8,
        onStepProgress: (StepExecutionReport) -> Unit = {}
    ): String {
        accessibilityService.awaitUiStabilization(quietPeriodMs = 300L, timeoutMs = 2500L)

        val historyLog = mutableListOf<String>()

        for (step in 1..maxSteps) {
            val preState = screenGrounder.groundScreen(
                rootNode = accessibilityService.rootInActiveWindow,
                screenshotProvider = { accessibilityService.captureScreenBitmap() },
                forceVisualSoM = false
            )

            if (fastCommitSendButton && step == 1) {
                val sendNode = preState.elements.values.firstOrNull { el ->
                    el.isClickable && (
                        el.viewIdResource?.equals("send", ignoreCase = true) == true ||
                        el.contentDescription?.equals("Send", ignoreCase = true) == true
                    )
                }
                if (sendNode != null) {
                    val fastCmd = GuiActionCommand(
                        thought = "Fast-path deep-link commit: clicking Send button [${sendNode.id}].",
                        action = GuiActionType.CLICK,
                        targetElementId = sendNode.id
                    )
                    val ok = ghostFinger.executeCommand(fastCmd, preState)
                    if (ok) {
                        return "Completed via Deep-Link + Ghost Finger fast commit on element [${sendNode.id}]."
                    }
                }
            }

            val command = requestNextGuiAction(
                userGoal = userGoal,
                screenTable = preState.promptContextTable,
                actionHistory = historyLog
            )

            if (command.action == GuiActionType.GOAL_COMPLETE) {
                return "Goal completed in ${step - 1} steps: ${command.thought}"
            }
            if (command.action == GuiActionType.ABORT_ERROR) {
                return "Aborted: ${command.thought}"
            }

            val dispatched = ghostFinger.executeCommand(command, preState)
            delay(150L)

            val postState = screenGrounder.groundScreen(
                rootNode = accessibilityService.rootInActiveWindow,
                screenshotProvider = { null },
                forceVisualSoM = false
            )

            val mutated = preState.stateHash != postState.stateHash
            val report = StepExecutionReport(
                stepIndex = step,
                command = command,
                actionDispatched = dispatched,
                preStateHash = preState.stateHash,
                postStateHash = postState.stateHash,
                uiMutated = mutated
            )
            onStepProgress(report)

            historyLog.add(
                "Step $step: ${command.action}(id=${command.targetElementId}, text=${command.textInput}) -> " +
                        "dispatched=$dispatched, uiChanged=$mutated"
            )
            Log.i(TAG, historyLog.last())
        }

        return "Reached maximum step limit ($maxSteps) for goal: $userGoal"
    }

    private suspend fun requestNextGuiAction(
        userGoal: String,
        screenTable: String,
        actionHistory: List<String>
    ): GuiActionCommand = suspendCancellableCoroutine { cont ->
        val prompt = """
            You are FRIDAY's Ghost Finger UI Navigator.
            GOAL: $userGoal
            PREVIOUS_STEPS:
            ${if (actionHistory.isEmpty()) "None" else actionHistory.joinToString("\n")}
            
            <untrusted_screen_data>
            $screenTable
            </untrusted_screen_data>
            
            Output strictly a single JSON object with keys:
            "thought": short reasoning,
            "action": one of [CLICK, LONG_CLICK, SET_TEXT, SET_TEXT_AND_ENTER, SCROLL_DOWN, SCROLL_UP, NAVIGATE_BACK, GOAL_COMPLETE, ABORT_ERROR],
            "target_id": integer element [ID] (or null),
            "text": string to type (or null).
        """.trimIndent()

        CoroutineScope(Dispatchers.IO).launch {
            npuEngine.generateFastResponse(
                userPrompt = prompt,
                screenBitmap = null,
                onToken = {},
                onComplete = { rawJson, _, _ ->
                    if (cont.isActive) {
                        cont.resume(parseActionJson(rawJson))
                    }
                },
                onError = { err ->
                    if (cont.isActive) {
                        cont.resume(
                            GuiActionCommand(
                                thought = "NPU error: $err",
                                action = GuiActionType.ABORT_ERROR
                            )
                        )
                    }
                }
            )
        }
    }

    private fun parseActionJson(rawOutput: String): GuiActionCommand {
        return try {
            val start = rawOutput.indexOf('{')
            val end = rawOutput.lastIndexOf('}')
            val cleaned = if (start != -1 && end != -1 && end > start) {
                rawOutput.substring(start, end + 1)
            } else {
                rawOutput
            }
            val json = JSONObject(cleaned)
            val actionStr = json.optString("action", "ABORT_ERROR").uppercase()
            val actionType = runCatching { GuiActionType.valueOf(actionStr) }
                .getOrDefault(GuiActionType.ABORT_ERROR)

            val targetId = if (json.has("target_id") && !json.isNull("target_id")) {
                json.optInt("target_id")
            } else null

            val text = if (json.has("text") && !json.isNull("text")) {
                json.optString("text")
            } else null

            GuiActionCommand(
                thought = json.optString("thought", ""),
                action = actionType,
                targetElementId = targetId,
                textInput = text
            )
        } catch (e: Exception) {
            GuiActionCommand(
                thought = "Failed to parse JSON: ${rawOutput.take(80)}",
                action = GuiActionType.ABORT_ERROR
            )
        }
    }
}
