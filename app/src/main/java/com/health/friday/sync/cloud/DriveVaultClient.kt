package com.health.friday.sync.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class DriveVaultFile(
    val id: String,
    val name: String,
    val modifiedTime: String? = null
)

internal data class DriveFilePage(
    val files: List<DriveVaultFile>,
    val nextPageToken: String?
)

/** JSON parser kept separate so pagination and field-order behavior can be unit tested. */
internal object DriveFilePageParser {
    fun parse(jsonText: String): DriveFilePage {
        val root = JSONObject(jsonText)
        val array = root.optJSONArray("files") ?: JSONArray()
        val files = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
                add(
                    DriveVaultFile(
                        id = id,
                        name = name,
                        modifiedTime = item.optString("modifiedTime").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        return DriveFilePage(
            files = files,
            nextPageToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
        )
    }
}

/**
 * Narrow Google Drive v3 client for FRIDAY's own app-created vault.
 * All calls use drive.file-scoped files created by this OAuth app.
 */
class DriveVaultClient {
    suspend fun ensureVaultFolder(accessToken: String): String = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank())
        val query = "name = '$VAULT_FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false"
        val existing = listAllFiles(accessToken, query)
            .firstOrNull { it.name == VAULT_FOLDER_NAME }
        if (existing != null) return@withContext existing.id

        val metadata = JSONObject()
            .put("name", VAULT_FOLDER_NAME)
            .put("mimeType", FOLDER_MIME)
            .put("appProperties", JSONObject().put("fridayApp", "FRIDAY"))
        val response = request(
            accessToken = accessToken,
            method = "POST",
            url = "$DRIVE_API/files?fields=id,name",
            contentType = "application/json; charset=UTF-8",
            body = metadata.toString().toByteArray(Charsets.UTF_8)
        )
        JSONObject(response).optString("id").takeIf { it.isNotBlank() }
            ?: throw IOException("Google Drive created the vault folder without returning an ID")
    }

    suspend fun uploadTextFile(
        accessToken: String,
        folderId: String,
        fileName: String,
        content: String,
        appProperties: Map<String, String> = emptyMap()
    ): String = withContext(Dispatchers.IO) {
        require(folderId.isNotBlank() && fileName.isNotBlank())
        val boundary = "friday_${java.util.UUID.randomUUID().toString().replace("-", "")}"
        val properties = JSONObject().apply {
            appProperties.forEach { (key, value) -> put(key, value) }
            put("fridayApp", "FRIDAY")
        }
        val metadata = JSONObject()
            .put("name", fileName)
            .put("parents", JSONArray().put(folderId))
            .put("appProperties", properties)
        val body = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content)
            append("\r\n--$boundary--\r\n")
        }.toByteArray(Charsets.UTF_8)

        val response = request(
            accessToken = accessToken,
            method = "POST",
            url = "$UPLOAD_API/files?uploadType=multipart&fields=id,name",
            contentType = "multipart/related; boundary=$boundary",
            body = body
        )
        JSONObject(response).optString("id").takeIf { it.isNotBlank() }
            ?: throw IOException("Google Drive upload succeeded without returning a file ID")
    }

    internal suspend fun findTextFile(
        accessToken: String,
        folderId: String,
        fileName: String
    ): DriveVaultFile? = withContext(Dispatchers.IO) {
        val escapedName = fileName.replace("'", "\\'")
        val query = "'$folderId' in parents and name = '$escapedName' and trashed = false"
        listAllFiles(accessToken, query).firstOrNull { it.name == fileName }
    }

    suspend fun downloadTextFile(accessToken: String, fileId: String): String = withContext(Dispatchers.IO) {
        require(fileId.isNotBlank())
        request(
            accessToken = accessToken,
            method = "GET",
            url = "$DRIVE_API/files/${encodePathSegment(fileId)}?alt=media"
        )
    }

    internal suspend fun listBatchFiles(accessToken: String, folderId: String): List<DriveVaultFile> =
        withContext(Dispatchers.IO) {
            val query = "'$folderId' in parents and trashed = false"
            listAllFiles(accessToken, query)
                .filter { it.name.startsWith(BATCH_FILE_PREFIX) && it.name.endsWith(BATCH_FILE_SUFFIX) }
                .sortedBy { it.name }
        }

    internal suspend fun findLatestRecoveryEnvelope(
        accessToken: String,
        folderId: String
    ): DriveVaultFile? = withContext(Dispatchers.IO) {
        val query = "'$folderId' in parents and trashed = false"
        listAllFiles(accessToken, query)
            .filter { it.name.startsWith(RECOVERY_FILE_PREFIX) && it.name.endsWith(RECOVERY_FILE_SUFFIX) }
            .maxByOrNull { file ->
                file.name.removePrefix(RECOVERY_FILE_PREFIX)
                    .removeSuffix(RECOVERY_FILE_SUFFIX)
                    .toLongOrNull() ?: 0L
            }
    }

    private fun listAllFiles(accessToken: String, query: String): List<DriveVaultFile> {
        val result = mutableListOf<DriveVaultFile>()
        var pageToken: String? = null
        val seenTokens = mutableSetOf<String>()
        do {
            val params = buildString {
                append("q=").append(encodeQuery(query))
                append("&pageSize=1000")
                append("&fields=nextPageToken,files(id,name,modifiedTime)")
                pageToken?.let { append("&pageToken=").append(encodeQuery(it)) }
            }
            val body = request(
                accessToken = accessToken,
                method = "GET",
                url = "$DRIVE_API/files?$params"
            )
            val page = DriveFilePageParser.parse(body)
            result += page.files
            val next = page.nextPageToken
            if (next != null) {
                check(seenTokens.add(next)) { "Google Drive repeated a page token; stopping pagination" }
            }
            pageToken = next
        } while (!pageToken.isNullOrBlank())
        return result
    }

    private fun request(
        accessToken: String,
        method: String,
        url: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (body != null) doOutput = true
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() }
                throw IOException("Google Drive $method failed (HTTP $code): ${message ?: response.take(800)}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun encodePathSegment(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val VAULT_FOLDER_NAME = "FRIDAY_Vault"
        const val RECOVERY_FILE_PREFIX = "friday_key_envelope_"
        const val RECOVERY_FILE_SUFFIX = ".json"
        const val BATCH_FILE_PREFIX = "batch_"
        const val BATCH_FILE_SUFFIX = ".friday"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}
