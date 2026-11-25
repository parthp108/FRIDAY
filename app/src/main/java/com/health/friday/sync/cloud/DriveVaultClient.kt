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

internal data class DriveChangesResult(
    val newFiles: List<DriveVaultFile>,
    val newPageToken: String
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
    @Volatile
    private var cachedFolderId: String? = null

    suspend fun ensureVaultFolder(accessToken: String): String = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank())
        cachedFolderId?.let { return@withContext it }

        val query = "name = '$VAULT_FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false"
        val existing = listAllFiles(accessToken, query)
            .firstOrNull { it.name == VAULT_FOLDER_NAME }
        if (existing != null) {
            cachedFolderId = existing.id
            return@withContext existing.id
        }

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
        val folderId = JSONObject(response).optString("id").takeIf { it.isNotBlank() }
            ?: throw IOException("Google Drive created the vault folder without returning an ID")
        cachedFolderId = folderId
        return@withContext folderId
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

    internal suspend fun fetchStartPageToken(accessToken: String): String = withContext(Dispatchers.IO) {
        val url = "$DRIVE_API/changes/startPageToken"
        val response = request(accessToken, "GET", url)
        JSONObject(response).getString("startPageToken")
    }

    internal suspend fun fetchChanges(
        accessToken: String,
        pageToken: String,
        folderId: String
    ): DriveChangesResult = withContext(Dispatchers.IO) {
        val files = mutableListOf<DriveVaultFile>()
        var currentToken: String? = pageToken
        var latestToken = pageToken

        while (!currentToken.isNullOrBlank()) {
            val url = "$DRIVE_API/changes?pageToken=$currentToken&fields=nextPageToken,newStartPageToken,changes(file(id,name,parents,trashed))"
            val response = request(accessToken, "GET", url)

            val root = JSONObject(response)
            val changesArray = root.optJSONArray("changes") ?: JSONArray()

            for (i in 0 until changesArray.length()) {
                val change = changesArray.optJSONObject(i) ?: continue
                val fileObj = change.optJSONObject("file") ?: continue
                val trashed = fileObj.optBoolean("trashed", false)
                if (trashed) continue

                val parents = fileObj.optJSONArray("parents") ?: JSONArray()
                var isInVault = false
                for (j in 0 until parents.length()) {
                    if (parents.optString(j) == folderId) {
                        isInVault = true
                        break
                    }
                }

                if (isInVault) {
                    val id = fileObj.optString("id")
                    val name = fileObj.optString("name")
                    if (id.isNotBlank() && name.isNotBlank()) {
                        files.add(DriveVaultFile(id = id, name = name))
                    }
                }
            }

            val nextToken = root.optString("nextPageToken").takeIf { it.isNotBlank() }
            val newStartToken = root.optString("newStartPageToken").takeIf { it.isNotBlank() }

            if (newStartToken != null) {
                latestToken = newStartToken
            }

            if (nextToken != null && nextToken != currentToken) {
                currentToken = nextToken
            } else {
                break
            }
        }

        DriveChangesResult(newFiles = files, newPageToken = latestToken)
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
