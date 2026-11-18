package com.health.friday.voice

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class FridaySpeechRecognizerTest {

    private lateinit var fakeContext: Context
    private lateinit var speechRecognizer: FridaySpeechRecognizer

    @Before
    fun setUp() {
        fakeContext = object : ContextWrapper(null) {
            override fun getApplicationContext(): Context = this
        }
        speechRecognizer = FridaySpeechRecognizer(fakeContext)
    }

    @Test
    fun testInitialState() {
        assertFalse(speechRecognizer.isListening.value)
        assertEquals("", speechRecognizer.partialResults.value)
        assertEquals(0f, speechRecognizer.rmsDb.value, 0.01f)
    }
}
