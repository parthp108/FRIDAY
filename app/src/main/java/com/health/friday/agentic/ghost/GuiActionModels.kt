package com.health.friday.agentic.ghost

enum class GuiActionType {
    CLICK,
    LONG_CLICK,
    SET_TEXT,
    SET_TEXT_AND_ENTER,
    SCROLL_DOWN,
    SCROLL_UP,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    NAVIGATE_BACK,
    NAVIGATE_HOME,
    WAIT_FOR_IDLE,
    GOAL_COMPLETE,
    ABORT_ERROR
}

data class GuiActionCommand(
    val thought: String,
    val action: GuiActionType,
    val targetElementId: Int? = null,
    val textInput: String? = null
)

data class StepExecutionReport(
    val stepIndex: Int,
    val command: GuiActionCommand,
    val actionDispatched: Boolean,
    val preStateHash: String,
    val postStateHash: String,
    val uiMutated: Boolean
)
