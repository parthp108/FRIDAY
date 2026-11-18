package com.health.friday.ai.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.health.friday.ai.AiTool
import com.health.friday.ai.AiToolParameter
import com.health.friday.spotify.SpotifyAuth
import com.health.friday.spotify.SpotifyWebApiClient
import com.health.friday.voice.FridayTtsEngine

class SpotifyAiTool(
    private val context: Context,
    private val spotifyAuth: SpotifyAuth? = null,
    private val apiClient: SpotifyWebApiClient? = null,
    private val ttsEngine: FridayTtsEngine? = null
) : AiTool {

    override val name = "play_spotify_music"
    override val description = "Plays a specific song, artist, album, or playlist on Spotify."

    override val parameters = listOf(
        AiToolParameter(
            name = "query",
            description = "The song, artist, album, or playlist to play on Spotify.",
            required = true
        )
    )

    override suspend fun execute(arguments: Map<String, String>): String {
        val query = arguments["query"]?.trim().orEmpty()
        if (query.isBlank()) {
            return "What should I play on Spotify?"
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$query"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.setPackage("com.spotify.music")
            context.startActivity(intent)
            "Playing '$query' on Spotify."
        } catch (e: Exception) {
            "Failed to launch Spotify. Ensure it is installed."
        }
    }
}
