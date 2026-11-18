package com.health.friday.vision.grounding

import com.health.friday.os.bridge.FridayAccessibilityService
import com.health.friday.inference.NpuInferenceEngine

class ScreenVisionCoordinator(
    private val grounder: HybridScreenGrounder,
    private val npuEngine: NpuInferenceEngine
) {

    /**
     * Invoked when the user says "Friday, summarize this screen" or when the
     * Agentic Loop needs to decide which UI element ID to interact with next.
     */
    suspend fun analyzeCurrentScreen(
        accessibilityService: FridayAccessibilityService,
        userGoal: String,
        forceVision: Boolean = false,
        onResult: (GroundedScreenState, String) -> Unit,
        onError: (String) -> Unit
    ) {
        val groundedState = grounder.groundScreen(
            rootNode = accessibilityService.rootInActiveWindow,
            screenshotProvider = { accessibilityService.captureScreenBitmap() },
            forceVisualSoM = forceVision
        )

        val groundingPrompt = """
            You are inspecting the user's live Android screen.
            <untrusted_screen_data>
            ${groundedState.promptContextTable}
            </untrusted_screen_data>
            
            User Goal: $userGoal
            If an action is required, reference the exact numeric [ID] from ELEMENTS.
        """.trimIndent()

        npuEngine.generateFastResponse(
            userPrompt = groundingPrompt,
            screenBitmap = groundedState.annotatedSomBitmap,
            onToken = { /* Optional live HUD token streaming */ },
            onComplete = { fullResponse, _, _ ->
                onResult(groundedState, fullResponse)
            },
            onError = onError
        )
    }
}
