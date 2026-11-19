package com.health.friday.desktop.sync.drive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Desktop OAuth using an installed-app loopback redirect and PKCE.
 * Only the long-lived refresh token and OAuth client secret are persisted,
 * encrypted with Windows DPAPI by DesktopDriveOAuthStore.
 */
class DesktopGoogleDriveOAuth internal constructor(
    private val store: DesktopDriveOAuthStore = DesktopDriveOAuthStore()
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun hasSavedAuthorization(): Boolean = store.load() != null

    /** First-time connection. The selected credentials JSON remains local. */
    fun connect(credentialsFile: File): String {
        require(credentialsFile.isFile) {
            "OAuth credentials file does not exist: ${credentialsFile.absolutePath}"
        }
        val root = json.parseToJsonElement(credentialsFile.readText()).jsonObject
        val installed = root["installed"]?.jsonObject
            ?: error("Select a Google Desktop OAuth client JSON file (the installed-app format).")
        val clientId = installed["client_id"]?.jsonPrimitive?.content
            ?: error("OAuth credentials are missing client_id.")
        val clientSecret = installed["client_secret"]?.jsonPrimitive?.content.orEmpty()

        val tokens = authorize(clientId, clientSecret)
        val refreshToken = tokens.refreshToken
            ?: error(
                "Google did not return a refresh token. Revoke FRIDAY's existing Google access " +
                    "and connect again so Google can issue an offline refresh token."
            )

        store.save(
            DesktopDriveOAuthCredentials(
                clientId = clientId,
                clientSecret = clientSecret,
                refreshToken = refreshToken
            )
        )
        return "Google authorization saved. The refresh token and client secret are encrypted locally with Windows DPAPI."
    }

    /** Uses the protected refresh token; no browser interaction is needed. */
    fun accessToken(): String {
        val saved = store.load()
            ?: error("Google Drive is not connected. Select the Desktop OAuth JSON and connect first.")
        val parameters = linkedMapOf(
            "client_id" to saved.clientId,
            "refresh_token" to saved.refreshToken,
            "grant_type" to "refresh_token"
        )
        if (saved.clientSecret.isNotBlank()) parameters["client_secret"] = saved.clientSecret

        val response = postForm(TOKEN_URL, parameters)
        val obj = json.parseToJsonElement(response).jsonObject
        val token = obj["access_token"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
            ?: error("Google token refresh response did not contain an access token.")
        // Google may rotate refresh tokens. Persist a replacement when supplied.
        val rotated = obj["refresh_token"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
        if (rotated != null && rotated != saved.refreshToken) {
            store.save(saved.copy(refreshToken = rotated))
        }
        return token
    }

    private fun authorize(clientId: String, clientSecret: String): TokenResponse {
        val verifier = randomUrlToken(48)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
        )
        val expectedState = randomUrlToken(32)

        check(Desktop.isDesktopSupported()) {
            "This PC cannot open a browser for Google authorization."
        }

        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 16)
            soTimeout = OAUTH_TIMEOUT_MS
        }
        val redirectUri = "http://127.0.0.1:${server.localPort}"
        val authParameters = linkedMapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to DRIVE_FILE_SCOPE,
            "state" to expectedState,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "access_type" to "offline",
            "prompt" to "consent"
        )
        val authUrl = AUTH_URL + "?" + authParameters.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }

        var authorizationCode: String? = null
        var authorizationError: String? = null
        try {
            Desktop.getDesktop().browse(URI(authUrl))
            while (authorizationCode == null && authorizationError == null) {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val requestLine = socket.getInputStream()
                        .bufferedReader(StandardCharsets.UTF_8)
                        .readLine().orEmpty()
                    val requestTarget = requestLine.split(" ").getOrNull(1).orEmpty()
                    val query = requestTarget.substringAfter('?', "")
                    val parameters = parseQuery(query)
                    val returnedState = parameters["state"]

                    if (returnedState != expectedState) {
                        sendHtml(socket, "FRIDAY authorization", "This local callback was rejected.")
                    } else {
                        val oauthError = parameters["error"]
                        if (!oauthError.isNullOrBlank()) {
                            authorizationError = "Google authorization failed: $oauthError"
                            sendHtml(socket, "Authorization failed", "Return to FRIDAY and try again.")
                        } else {
                            authorizationCode = parameters["code"]?.takeIf { it.isNotBlank() }
                            sendHtml(
                                socket,
                                "FRIDAY Google Drive connected",
                                "You can close this tab and return to the FRIDAY desktop app."
                            )
                        }
                    }
                }
            }
        } finally {
            runCatching { server.close() }
        }

        authorizationError?.let { error(it) }
        val code = authorizationCode ?: error("Google did not return an authorization code.")
        val exchange = linkedMapOf(
            "code" to code,
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "grant_type" to "authorization_code",
            "code_verifier" to verifier
        )
        if (clientSecret.isNotBlank()) exchange["client_secret"] = clientSecret
        val tokenJson = json.parseToJsonElement(postForm(TOKEN_URL, exchange)).jsonObject
        val access = tokenJson["access_token"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
            ?: error("Google did not return an access token.")
        val refresh = tokenJson["refresh_token"]?.jsonPrimitive?.content
            ?.takeIf { it.isNotBlank() }
        return TokenResponse(access, refresh)
    }

    private fun postForm(url: String, parameters: Map<String, String>): String {
        val body = parameters.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }.toByteArray(StandardCharsets.UTF_8)
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = HTTP_TIMEOUT_MS
            readTimeout = HTTP_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    json.parseToJsonElement(response).jsonObject["error_description"]
                        ?.jsonPrimitive?.content
                }.getOrNull()
                error("Google OAuth request failed (HTTP $code): ${message ?: response.take(800)}")
            }
            return response
        } finally {
            body.fill(0)
            connection.disconnect()
        }
    }

    private fun sendHtml(socket: Socket, title: String, message: String) {
        val safeTitle = htmlEscape(title)
        val safeMessage = htmlEscape(message)
        val html = """
            <!doctype html><html><head><meta charset="utf-8"></head>
            <body style="font-family:Arial,sans-serif;text-align:center;padding:48px;background:#0F172A;color:#F8FAFC;">
              <h2 style="color:#38BDF8;">$safeTitle</h2>
              <p style="color:#94A3B8;">$safeMessage</p>
            </body></html>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: ${html.size}\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().apply {
            write(headers.toByteArray(StandardCharsets.UTF_8))
            write(html)
            flush()
        }
    }

    private fun parseQuery(query: String): Map<String, String> = query
        .split('&')
        .filter { it.isNotBlank() }
        .associate { part ->
            val pieces = part.split('=', limit = 2)
            URLDecoder.decode(pieces[0], StandardCharsets.UTF_8) to
                URLDecoder.decode(pieces.getOrElse(1) { "" }, StandardCharsets.UTF_8)
        }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun randomUrlToken(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            .also { bytes.fill(0) }
    }

    private fun htmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private data class TokenResponse(val accessToken: String, val refreshToken: String?)

    private companion object {
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        private const val OAUTH_TIMEOUT_MS = 180_000
        private const val HTTP_TIMEOUT_MS = 30_000
    }
}
