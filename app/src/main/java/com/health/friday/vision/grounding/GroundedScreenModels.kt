package com.health.friday.vision.grounding

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

enum class ElementSource {
    ACCESSIBILITY_DOM,
    ASSIST_STRUCTURE,
    VISION_OCR_FALLBACK
}

data class GroundedUiElement(
    val id: Int,
    val source: ElementSource,
    val viewIdResource: String?,
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val boundsInScreen: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    @Transient val liveNodeRef: AccessibilityNodeInfo? = null
) {
    val centerX: Float get() = boundsInScreen.exactCenterX()
    val centerY: Float get() = boundsInScreen.exactCenterY()

    fun toPromptLine(): String {
        val actions = buildList {
            if (isClickable) add("CLICK")
            if (isEditable) add("EDIT")
            if (isScrollable) add("SCROLL")
        }.joinToString(",")

        val label = listOfNotNull(text, contentDescription)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
            .take(100)

        return "[$id] ${className ?: "Node"} " +
                "${if (viewIdResource != null) "id=$viewIdResource " else ""}" +
                "(${actions.ifEmpty { "READ" }}) " +
                "\"$label\" @ [${boundsInScreen.centerX()},${boundsInScreen.centerY()}]"
    }
}

data class GroundedScreenState(
    val timestamp: Long,
    val packageName: String,
    val stateHash: String,
    val elements: Map<Int, GroundedUiElement>,
    val annotatedSomBitmap: Bitmap?,
    val promptContextTable: String,
    val usedVisionFallback: Boolean
)
