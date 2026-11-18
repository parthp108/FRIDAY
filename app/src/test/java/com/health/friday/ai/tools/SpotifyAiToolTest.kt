package com.health.friday.ai.tools

import android.content.Context
import android.content.ContextWrapper
import com.health.friday.spotify.SpotifyAuth
import com.health.friday.spotify.SpotifyWebApiClient
import com.health.friday.voice.FridayTtsEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAiToolTest {

    private val fakeContext: Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }
    private val fakeAuth = SpotifyAuth(fakeContext)
    private val fakeApiClient = SpotifyWebApiClient(fakeContext, fakeAuth)
    private val fakeTtsEngine = FridayTtsEngine(fakeContext)
    private val tool = SpotifyAiTool(fakeContext, fakeAuth, fakeApiClient, fakeTtsEngine)

    @Test
    fun declaresRequiredQueryParameter() {
        assertTrue(tool.parameters.any { it.name == "query" && it.required })
    }

    @Test
    fun keepsToolName() {
        assertEquals("play_spotify_music", tool.name)
    }

    @Test
    fun blankQuery_asksWhatToPlay() = runBlocking {
        val result = tool.execute(mapOf("query" to "   "))
        assertTrue(result.contains("What should I play"))
    }

    @Test
    fun missingQuery_asksWhatToPlay() = runBlocking {
        val result = tool.execute(emptyMap())
        assertTrue(result.contains("What should I play"))
    }
}
