package com.health.friday.desktop.sync.drive

import kotlinx.serialization.json.*
import java.awt.Desktop
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.Base64
import java.util.UUID

object DesktopDriveTest {

    private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    private const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private const val DRIVE_URL = "https://www.googleapis.com/drive/v3"

    private fun getHttpClient(): HttpClient {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build()
    }

    fun run(credentialsFile: File): String {
        require(credentialsFile.exists()) {
            "OAuth credentials file does not exist: ${credentialsFile.absolutePath}"
        }

        val root = Json.parseToJsonElement(credentialsFile.readText()).jsonObject
        val installed = root["installed"]?.jsonObject
            ?: error("This is not a Desktop OAuth JSON file. Select the downloaded Desktop client JSON.")

        val clientId = installed["client_id"]?.jsonPrimitive?.content
            ?: error("OAuth JSON is missing client_id.")

        val clientSecret = installed["client_secret"]?.jsonPrimitive?.content.orEmpty()

        val accessToken = authorize(clientId, clientSecret)

        // Find the Android-created FRIDAY folder.
        val folderQuery =
            "name = 'FRIDAY' and mimeType = 'application/vnd.google-apps.folder' " +
            "and trashed = false and appProperties has { key='fridayApp' and value='FRIDAY' }"

        val folderList = driveGet(
            accessToken,
            "$DRIVE_URL/files?q=${encode(folderQuery)}&spaces=drive&pageSize=100&fields=files(id,name)"
        )

        val folders = Json.parseToJsonElement(folderList)
            .jsonObject["files"]?.jsonArray ?: JsonArray(emptyList())

        val folderId = folders.firstOrNull()
            ?.jsonObject?.get("id")?.jsonPrimitive?.content
            ?: error(
                "Could not find the Android FRIDAY folder. " +
                "Check that you selected the same Google account and Cloud project."
            )

        // Upload a unique test file inside the existing folder.
        val fileName = "friday-desktop-drive-test-${System.currentTimeMillis()}.txt"
        val payload = """
            FRIDAY Windows Desktop Drive test
            Created: ${System.currentTimeMillis()}
            Device: ${System.getenv("COMPUTERNAME") ?: "Windows Desktop"}
        """.trimIndent()

        val boundary = "friday_${UUID.randomUUID().toString().replace("-", "")}"

        val metadata = """
            {
              "name": "$fileName",
              "mimeType": "text/plain",
              "parents": ["$folderId"],
              "appProperties": {
                "fridayApp": "FRIDAY"
              }
            }
        """.trimIndent()

        val multipartBody = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n\r\n")
            append(payload)
            append("\r\n--$boundary--\r\n")
        }

        val uploadRequest = HttpRequest.newBuilder()
            .uri(
                URI(
                    "https://www.googleapis.com/upload/drive/v3/files" +
                    "?uploadType=multipart&fields=id,name"
                )
            )
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "multipart/related; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofString(multipartBody))
            .build()

        val uploadResponse = getHttpClient().send(
            uploadRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        requireSuccess(uploadResponse.statusCode(), uploadResponse.body(), "Upload")

        val fileId = Json.parseToJsonElement(uploadResponse.body())
            .jsonObject["id"]?.jsonPrimitive?.content
            ?: error("Upload succeeded but Google did not return a file ID.")

        // Download the uploaded file.
        val downloadRequest = HttpRequest.newBuilder()
            .uri(URI("$DRIVE_URL/files/$fileId?alt=media"))
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()

        val downloadResponse = getHttpClient().send(
            downloadRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        requireSuccess(downloadResponse.statusCode(), downloadResponse.body(), "Download")

        check(downloadResponse.body() == payload) {
            "Downloaded content does not match the uploaded content."
        }

        return """
            Authorization: successful
            Folder: FRIDAY
            Folder ID: $folderId

            Upload: successful
            Download: successful
            Content comparison: passed

            File: $fileName
            File ID: $fileId
        """.trimIndent()
    }

    private fun authorize(clientId: String, clientSecret: String): String {
        val verifier = randomToken(32)
        val challenge = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                MessageDigest.getInstance("SHA-256")
                    .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
            )

        val state = randomToken(32)

        // Use ServerSocket with reuseAddress and a large connection backlog (50) for browser probes
        val serverSocket = ServerSocket()
        serverSocket.reuseAddress = true
        serverSocket.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 50)
        serverSocket.soTimeout = 180_000 // 3 minutes timeout

        val port = serverSocket.localPort
        val redirectUri = "http://127.0.0.1:$port"

        val authParams = linkedMapOf(
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "response_type" to "code",
            "scope" to SCOPE,
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "access_type" to "offline",
            "prompt" to "select_account"
        )

        val authUrl =
            "https://accounts.google.com/o/oauth2/v2/auth?" +
            authParams.entries.joinToString("&") {
                "${encode(it.key)}=${encode(it.value)}"
            }

        check(Desktop.isDesktopSupported()) {
            "This system cannot open a browser for Google authorization."
        }

        // Open default browser
        Desktop.getDesktop().browse(URI(authUrl))

        var capturedCode: String? = null
        var capturedError: String? = null

        try {
            while (capturedCode == null && capturedError == null) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    if (capturedCode != null) break
                    throw e
                }

                socket.use { s ->
                    val reader = s.getInputStream().bufferedReader(StandardCharsets.UTF_8)
                    val requestLine = reader.readLine().orEmpty()
                    if (requestLine.isBlank()) return@use

                    // Request line format: "GET /?code=...&state=... HTTP/1.1"
                    val uriString = requestLine.split(" ").getOrNull(1).orEmpty()
                    if (uriString.isBlank() || uriString.startsWith("/favicon.ico")) {
                        val out = s.getOutputStream()
                        val res = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                        out.write(res.toByteArray(StandardCharsets.UTF_8))
                        out.flush()
                        return@use
                    }

                    val queryParams = parseQuery(uriString.substringAfter("?", ""))

                    val returnedState = queryParams["state"]
                    val returnedCode = queryParams["code"]
                    val oauthError = queryParams["error"]

                    if (returnedState == state && !returnedCode.isNullOrBlank()) {
                        capturedCode = returnedCode
                        sendHtmlResponse(
                            s,
                            "FRIDAY Google Drive Connected!",
                            "You can close this tab and return to the FRIDAY desktop app."
                        )
                    } else if (oauthError != null) {
                        capturedError = "Google authorization failed: $oauthError"
                        sendHtmlResponse(s, "Authorization Failed", oauthError)
                    }
                }
            }
        } finally {
            runCatching { serverSocket.close() }
        }

        if (capturedError != null) error(capturedError)
        val code = capturedCode ?: error("Google did not return an authorization code.")

        // Exchange Authorization Code for Access Token
        val tokenParams = linkedMapOf(
            "code" to code,
            "client_id" to clientId,
            "redirect_uri" to redirectUri,
            "grant_type" to "authorization_code",
            "code_verifier" to verifier
        )

        if (clientSecret.isNotBlank()) {
            tokenParams["client_secret"] = clientSecret
        }

        val tokenBody = tokenParams.entries.joinToString("&") {
            "${encode(it.key)}=${encode(it.value)}"
        }

        val tokenRequest = HttpRequest.newBuilder()
            .uri(URI(TOKEN_URL))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
            .build()

        val tokenResponse = getHttpClient().send(
            tokenRequest,
            HttpResponse.BodyHandlers.ofString()
        )

        requireSuccess(
            tokenResponse.statusCode(),
            tokenResponse.body(),
            "OAuth token exchange"
        )

        return Json.parseToJsonElement(tokenResponse.body())
            .jsonObject["access_token"]?.jsonPrimitive?.content
            ?: error("Google did not return an access token.")
    }

    private fun sendHtmlResponse(socket: java.net.Socket, title: String, message: String) {
        runCatching {
            val html = """
                <!doctype html>
                <html>
                <body style="font-family:Arial,sans-serif;text-align:center;padding:48px;background:#0F172A;color:#F8FAFC;">
                  <h2 style="color:#38BDF8;">$title</h2>
                  <p style="color:#94A3B8;">$message</p>
                </body>
                </html>
            """.trimIndent()
            val bytes = html.toByteArray(StandardCharsets.UTF_8)
            val out = socket.getOutputStream()
            val header = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${bytes.size}\r\n" +
                    "Connection: close\r\n\r\n"
            out.write(header.toByteArray(StandardCharsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }

    private fun driveGet(token: String, url: String): String {
        val request = HttpRequest.newBuilder()
            .uri(URI(url))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        val response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
        requireSuccess(response.statusCode(), response.body(), "Drive request")
        return response.body()
    }

    private fun requireSuccess(status: Int, body: String, operation: String) {
        if (status !in 200..299) {
            val message = runCatching {
                Json.parseToJsonElement(body)
                    .jsonObject["error"]?.jsonObject
                    ?.get("message")?.jsonPrimitive?.content
            }.getOrNull()

            error("$operation failed (HTTP $status): ${message ?: body.take(1000)}")
        }
    }

    private fun randomToken(bytes: Int): String {
        val data = ByteArray(bytes)
        SecureRandom().nextBytes(data)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun parseQuery(query: String?): Map<String, String> =
        query.orEmpty()
            .split("&")
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split("=", limit = 2)
                URLDecoder.decode(parts[0], StandardCharsets.UTF_8) to
                    URLDecoder.decode(parts.getOrElse(1) { "" }, StandardCharsets.UTF_8)
            }
}
