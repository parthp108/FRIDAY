package com.health.friday.voice.pipeline

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AmbientAudioPipelineTest {

    private fun mockContext(): Context = object : ContextWrapper(null) {}

    @Test
    fun testSileroVadEngineFallbackAndEvaluate() {
        val vad = SileroVadEngine(mockContext())
        val silentBuffer = ShortArray(512) { 0 }
        val loudBuffer = ShortArray(512) { 15000 }

        val silentProb = vad.evaluate(silentBuffer)
        val loudProb = vad.evaluate(loudBuffer)

        assertEquals(0f, silentProb, 0.01f)
        assertTrue(loudProb > 0.5f)
    }

    @Test
    fun testWakewordEngineDetection() {
        val kws = WakewordEngine(mockContext())
        val silentFrame = ShortArray(512) { 50 }
        val loudPeakFrame = ShortArray(512) { 8000 }

        assertFalse(kws.detect(silentFrame))
        assertTrue(kws.detect(loudPeakFrame))
    }

    @Test
    fun testStreamingAsrAccumulationAndReset() {
        val asr = StreamingAsrEngine(mockContext())
        val frame = ShortArray(512) { 1000 }

        asr.reset()
        for (i in 0..10) {
            asr.feedAudio(frame)
        }

        val result = asr.finalizeStream()
        assertTrue(result.contains("Audio Stream Received"))
    }
}
