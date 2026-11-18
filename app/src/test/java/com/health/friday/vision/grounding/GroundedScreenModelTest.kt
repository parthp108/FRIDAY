package com.health.friday.vision.grounding

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundedScreenModelTest {

    @Test
    fun testGroundedUiElementPromptLine() {
        val element = GroundedUiElement(
            id = 5,
            source = ElementSource.ACCESSIBILITY_DOM,
            viewIdResource = "search_bar",
            className = "EditText",
            text = "Search query",
            contentDescription = "Search input",
            boundsInScreen = Rect(0, 0, 100, 50),
            isClickable = true,
            isEditable = true,
            isScrollable = false
        )

        val promptLine = element.toPromptLine()
        assertTrue(promptLine.contains("[5]"))
        assertTrue(promptLine.contains("CLICK,EDIT"))
        assertTrue(promptLine.contains("Search query | Search input"))
    }

    @Test
    fun testElementSourceValues() {
        assertEquals(3, ElementSource.entries.size)
    }
}
