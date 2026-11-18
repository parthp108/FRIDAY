package com.health.friday.spotify

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.max

data class SpotifyMatch(
    val uri: String,
    val name: String,
    val artistName: String,
    val score: Double
)

class SpotifyWebApiClient(
    private val context: Context,
    private val spotifyAuth: SpotifyAuth
) {

    /**
     * Searches Spotify's catalog using Web API and returns the best match.
     * Ranks results using Spotify's official popularity metric and top search position.
     */
    suspend fun findBestMatch(query: String): SpotifyMatch? = withContext(Dispatchers.IO) {
        val token = spotifyAuth.getAccessToken() ?: return@withContext null

        val searchUrl = "$API_BASE/search?q=${URLEncoder.encode(query, "UTF-8")}&type=track,playlist&limit=10"
        val response = try {
            SpotifyHttp.request(
                method = "GET",
                url = searchUrl,
                headers = mapOf("Authorization" to "Bearer $token")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Search request failed", e)
            return@withContext null
        }

        if (!response.isSuccess) return@withContext null
        parseAndScoreMatches(response.body, query)
    }

    /**
     * Sends a direct Web API play command for a given track/playlist URI.
     * Targets available device ID to force remote playback.
     */
    suspend fun playUri(uri: String): Boolean = withContext(Dispatchers.IO) {
        val token = spotifyAuth.getAccessToken() ?: return@withContext false
        var deviceId = getActiveDeviceId(token)

        if (deviceId == null) {
            wakeSpotifyBackground()
            delay(300)
            deviceId = getActiveDeviceId(token)
        }

        val playUrl = if (deviceId != null) "$API_BASE/me/player/play?device_id=$deviceId" else "$API_BASE/me/player/play"
        val payload = if (uri.contains(":playlist:")) {
            JSONObject().apply { put("context_uri", uri) }.toString()
        } else {
            JSONObject().apply { put("uris", JSONArray().put(uri)) }.toString()
        }

        val response = try {
            SpotifyHttp.request(
                method = "PUT",
                url = playUrl,
                headers = mapOf(
                    "Authorization" to "Bearer $token",
                    "Content-Type" to "application/json"
                ),
                body = payload
            )
        } catch (e: Exception) {
            Log.e(TAG, "Play Web API request failed", e)
            return@withContext false
        }

        Log.i(TAG, "Web API play response code: ${response.code} (device: $deviceId)")
        response.isSuccess || response.code == 204
    }

    private fun wakeSpotifyBackground() {
        try {
            val mediaIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                setPackage("com.spotify.music")
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            context.sendBroadcast(mediaIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Background media broadcast failed", e)
        }
    }

    private fun getActiveDeviceId(token: String): String? {
        return try {
            val response = SpotifyHttp.request(
                method = "GET",
                url = "$API_BASE/me/player/devices",
                headers = mapOf("Authorization" to "Bearer $token")
            )
            if (!response.isSuccess) return null

            val json = JSONObject(response.body)
            val devices = json.optJSONArray("devices") ?: return null

            var firstId: String? = null
            for (i in 0 until devices.length()) {
                val dev = devices.getJSONObject(i)
                val id = dev.optString("id")
                if (dev.optBoolean("is_active", false)) {
                    return id
                }
                if (firstId == null && id.isNotEmpty()) {
                    firstId = id
                }
            }
            firstId
        } catch (e: Exception) {
            null
        }
    }

    private fun parseAndScoreMatches(responseBody: String, query: String): SpotifyMatch? {
        val root = try { JSONObject(responseBody) } catch (_: Exception) { return null }
        val matches = mutableListOf<SpotifyMatch>()
        val queryLower = query.lowercase(Locale.ENGLISH).trim()

        val tracksObj = root.optJSONObject("tracks")
        val items = tracksObj?.optJSONArray("items")
        if (items != null && items.length() > 0) {
            val totalItems = items.length()
            for (i in 0 until totalItems) {
                val item = items.getJSONObject(i)
                val uri = item.optString("uri")
                val name = item.optString("name")
                val popularity = item.optInt("popularity", 0)
                val artists = item.optJSONArray("artists")
                val artistName = if (artists != null && artists.length() > 0) {
                    artists.getJSONObject(0).optString("name")
                } else ""

                val nameLower = name.lowercase(Locale.ENGLISH)
                val artistLower = artistName.lowercase(Locale.ENGLISH)

                val nameSimilarity = jaroWinklerSimilarity(queryLower, nameLower)
                val artistBonus = if (queryLower.contains(artistLower) && artistLower.isNotEmpty()) 0.25 else 0.0
                val popularityNorm = popularity / 100.0
                val positionWeight = (totalItems - i).toDouble() / totalItems.toDouble()

                val score = (0.40 * popularityNorm) + (0.40 * positionWeight) + (0.15 * nameSimilarity) + artistBonus
                matches.add(SpotifyMatch(uri, name, artistName, score))
            }
        }

        val best = matches.maxByOrNull { it.score }
        if (best != null) {
            Log.i(TAG, "Selected Best Match: '${best.name}' by '${best.artistName}' (URI: ${best.uri}, Score: ${best.score})")
        }
        return best
    }

    private fun jaroWinklerSimilarity(s1: String, s2: String): Double {
        val jaro = jaroDistance(s1, s2)
        val prefixLength = s1.zip(s2).takeWhile { (c1, c2) -> c1 == c2 }.count().coerceAtMost(4)
        return jaro + (prefixLength * 0.1 * (1.0 - jaro))
    }

    private fun jaroDistance(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        val len1 = s1.length
        val len2 = s2.length
        if (len1 == 0 || len2 == 0) return 0.0

        val matchDistance = max(len1, len2) / 2 - 1
        if (matchDistance < 0) return 0.0

        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)

        var matches = 0
        for (i in 0 until len1) {
            val start = max(0, i - matchDistance)
            val end = (i + matchDistance + 1).coerceAtMost(len2)

            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0.0
        var k = 0
        for (i in 0 until len1) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val m = matches.toDouble()
        return (m / len1 + m / len2 + (m - transpositions / 2.0) / m) / 3.0
    }

    private companion object {
        const val TAG = "FRIDAY_SPOTIFY_API"
        const val API_BASE = "https://api.spotify.com/v1"
    }
}
