package com.health.friday.voice

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FridayTtsEngineTest {

    private lateinit var fakeContext: Context
    private lateinit var engine: FridayTtsEngine

    @Before
    fun setUp() {
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        engine = FridayTtsEngine(fakeContext)
    }

    @Test
    fun testCleanAndEnhanceForSpeech_expandsUnitsAndStripsMarkdown() {
        val rawText = "Logged 500ml water and 1500kcal! 💧"
        val clean = engine.cleanAndEnhanceForSpeech(rawText)

        assertEquals("Logged 500 milliliters water and 1500 calories!", clean)
    }

    @Test
    fun testDetermineEmotion_enthusiastic() {
        val text = "Great job logging your water today!"
        val emotion = engine.determineEmotion(text)

        assertEquals(1.12f, emotion.pitch, 0.01f)
        assertEquals(1.05f, emotion.rate, 0.01f)
    }

    @Test
    fun testDetermineEmotion_empatheticWarning() {
        val text = "Warning: you haven't logged any meals today."
        val emotion = engine.determineEmotion(text)

        assertEquals(0.98f, emotion.pitch, 0.01f)
        assertEquals(0.96f, emotion.rate, 0.01f)
    }

    @Test
    fun testDetermineEmotion_inquisitive() {
        val text = "Would you like me to set a reminder for 5 PM?"
        val emotion = engine.determineEmotion(text)

        assertEquals(1.08f, emotion.pitch, 0.01f)
        assertEquals(1.02f, emotion.rate, 0.01f)
    }
}
