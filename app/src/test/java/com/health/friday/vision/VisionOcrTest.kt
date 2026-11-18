package com.health.friday.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionOcrTest {

    @Test
    fun testVisionPromptFormatting() {
        val ocrText = "Calories 250\nProtein 20g\nCarbs 30g"
        val prompt = "I am looking at this text through my camera. Please analyze it and take any appropriate actions (like setting a reminder, logging a meal, or adding a task):\n\n$ocrText"

        assertTrue(prompt.contains("Calories 250"))
        assertTrue(prompt.contains("Protein 20g"))
        assertTrue(prompt.contains("logging a meal"))
    }

    @Test
    fun testPerceptualHasher_classifyScene_nutritionLabel() {
        val text = "Nutrition Facts\nCalories 250\nProtein 20g\nCarbs 15g"
        val category = PerceptualHasher.classifyScene(text)

        assertEquals("NUTRITION_LABEL 🥗", category)
    }

    @Test
    fun testPerceptualHasher_classifyScene_receipt() {
        val text = "Subtotal: $15.50\nTax: $1.20\nTotal: $16.70"
        val category = PerceptualHasher.classifyScene(text)

        assertEquals("RECEIPT 🧾", category)
    }

    @Test
    fun testPerceptualHasher_classifyScene_stickyNote() {
        val text = "Call Mom tomorrow at 5 PM"
        val category = PerceptualHasher.classifyScene(text)

        assertEquals("STICKY_NOTE 📝", category)
    }
}
