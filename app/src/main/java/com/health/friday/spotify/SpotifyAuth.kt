package com.health.friday.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

data class SpotifyProfile(val name: String, val isPremium: Boolean)
data class SpotifyLoginResult(val ok: Boolean, val message: String)

/**
 * Owns the Spotify login: starts it, finishes it, stores the tokens and keeps the access token fresh.
 * Everything else in FRIDAY asks this class for a token; nothing else touches the tokens.
 */
class SpotifyAuth(context: Context) {

    private val appContext by lazy { context.applicationContext }
    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    private val refreshMutex = Mutex()

    fun isConnected(): Boolean = !prefs.getString(KEY_REFRESH, null).isNullOrEmpty()

    /** Opens Spotify's login page in the browser. Returns false if it could not be opened. */
    fun startLogin(): Boolean {
        if (!SpotifyConfig.isConfigured()) return false

        val verifier = PkceUtil.newVerifier()
        val state = PkceUtil.newState()
        prefs.edit()
            .putString(KEY_PENDING_VERIFIER, verifier)
            .putString(KEY_PENDING_STATE, state)
            .apply()

        val loginUrl = Uri.parse(AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", SpotifyConfig.CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", SpotifyConfig.REDIRECT_URI)
            .appendQueryParameter("scope", SpotifyConfig.SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", PkceUtil.challengeFor(verifier))
            .appendQueryParameter("state", state)
            .build()

        return try {
            appContext.startActivity(
                Intent(Intent.ACTION_VIEW, loginUrl).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the Spotify login page", e)
            false
        }
    }

    /** Called with the redirect Spotify sends back after you approve. */
    suspend fun completeLogin(callback: Uri): SpotifyLoginResult = withContext(Dispatchers.IO) {
        val error = callback.getQueryParameter("error")
        val code = callback.getQueryParameter("code")
        val state = callback.getQueryParameter("state")
        val expectedState = prefs.getString(KEY_PENDING_STATE, null)
        val verifier = prefs.getString(KEY_PENDING_VERIFIER, null)
        clearPending()   // a login attempt can only be completed once

        if (error != null) {
            return@withContext SpotifyLoginResult(false, "Spotify login was not completed ($error).")
        }
        if (code.isNullOrEmpty() || verifier == null || expectedState == null || state != expectedState) {
            return@withContext SpotifyLoginResult(
                false,
                "That Spotify login link wasn't expected, so I ignored it. Ask me to play something to start again."
            )
        }

        val response = try {
            SpotifyHttp.postForm(
                TOKEN_URL,
                mapOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to SpotifyConfig.REDIRECT_URI,
                    "client_id" to SpotifyConfig.CLIENT_ID,
                    "code_verifier" to verifier
                )
            )
        } catch (e: IOException) {
            return@withContext SpotifyLoginResult(false, "I couldn't reach Spotify to finish the login: ${e.message}")
        }

        if (!response.isSuccess) {
            Log.w(TAG, "Token exchange failed: HTTP ${response.code} ${errorSummary(response.body)}")
            return@withContext SpotifyLoginResult(
                false,
                "Spotify refused the login (HTTP ${response.code}). Check the Client ID and the redirect URI in the Spotify dashboard."
            )
        }
        if (!saveTokens(response.body)) {
            return@withContext SpotifyLoginResult(false, "Spotify's reply didn't contain a token.")
        }

        val profile = fetchProfile()
        val message = when {
            profile == null -> "Spotify connected."
            profile.isPremium -> "Spotify connected as ${profile.name} (Premium)."
            else -> "Spotify connected as ${profile.name}, but this account isn't Premium, so I can't control playback."
        }
        SpotifyLoginResult(true, message)
    }

    /** A valid access token, refreshed automatically when it has expired. Null if not connected. */
    suspend fun getAccessToken(): String? {
        cachedValidToken()?.let { return it }
        return refreshMutex.withLock { cachedValidToken() ?: refreshAccessToken() }
    }

    suspend fun fetchProfile(): SpotifyProfile? {
        val token = getAccessToken() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val response = SpotifyHttp.request(
                    "GET",
                    "$API_BASE/me",
                    mapOf("Authorization" to "Bearer $token")
                )
                if (!response.isSuccess) {
                    Log.w(TAG, "Profile request failed: HTTP ${response.code}")
                    return@withContext null
                }
                val json = JSONObject(response.body)
                val name = json.optString("display_name").ifEmpty { json.optString("id", "your account") }
                SpotifyProfile(name, json.optString("product") == "premium")
            } catch (e: Exception) {
                Log.w(TAG, "Profile request failed: ${e.message}")
                null
            }
        }
    }

    fun disconnect() {
        prefs.edit().clear().apply()
    }

    // ---------- internals ----------

    private fun cachedValidToken(): String? {
        val token = prefs.getString(KEY_ACCESS, null) ?: return null
        return if (System.currentTimeMillis() < prefs.getLong(KEY_EXPIRES_AT, 0L)) token else null
    }

    private suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return@withContext null

        val response = try {
            SpotifyHttp.postForm(
                TOKEN_URL,
                mapOf(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refresh,
                    "client_id" to SpotifyConfig.CLIENT_ID
                )
            )
        } catch (e: IOException) {
            Log.w(TAG, "Token refresh failed: ${e.message}")
            return@withContext null
        }

        when {
            response.isSuccess ->
                if (saveTokens(response.body)) prefs.getString(KEY_ACCESS, null) else null

            response.code == 400 || response.code == 401 -> {
                Log.w(TAG, "Refresh token rejected: ${errorSummary(response.body)}")
                disconnect()
                null
            }

            else -> {
                Log.w(TAG, "Token refresh failed: HTTP ${response.code}")
                null
            }
        }
    }

    private fun saveTokens(body: String): Boolean {
        return try {
            val json = JSONObject(body)
            val access = json.optString("access_token")
            if (access.isEmpty()) return false
            val refresh = json.optString("refresh_token")
            val expiresInSeconds = json.optLong("expires_in", 3600L)

            val editor = prefs.edit()
            editor.putString(KEY_ACCESS, access)
            editor.putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (expiresInSeconds - 60L) * 1000L)
            if (refresh.isNotEmpty()) editor.putString(KEY_REFRESH, refresh)
            editor.apply()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not read Spotify's token reply", e)
            false
        }
    }

    private fun clearPending() {
        prefs.edit().remove(KEY_PENDING_VERIFIER).remove(KEY_PENDING_STATE).apply()
    }

    private fun errorSummary(body: String): String = try {
        val json = JSONObject(body)
        "${json.optString("error")}: ${json.optString("error_description")}"
    } catch (e: Exception) {
        ""
    }

    private companion object {
        const val TAG = "FRIDAY_SPOTIFY"
        const val PREFS_NAME = "friday_spotify"
        const val AUTHORIZE_URL = "https://accounts.spotify.com/authorize"
        const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        const val API_BASE = "https://api.spotify.com/v1"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES_AT = "expires_at_ms"
        const val KEY_PENDING_VERIFIER = "pending_verifier"
        const val KEY_PENDING_STATE = "pending_state"
    }
}
