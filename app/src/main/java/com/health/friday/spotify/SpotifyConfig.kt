package com.health.friday.spotify

object SpotifyConfig {
    /** Paste the Client ID from your Spotify developer dashboard. It is public (PKCE has no secret). */
    const val CLIENT_ID = "30606c92a8c4456cae554bb692331451"

    /** Must match the Redirect URI in the dashboard AND the manifest entry exactly. */
    const val REDIRECT_URI = "com.health.friday://spotify-callback"

    /** profile (to see if the account is Premium), read devices, start playback */
    const val SCOPES = "user-read-private user-read-playback-state user-modify-playback-state"

    fun isConfigured(): Boolean = CLIENT_ID.isNotBlank() && !CLIENT_ID.startsWith("PASTE_")
}
