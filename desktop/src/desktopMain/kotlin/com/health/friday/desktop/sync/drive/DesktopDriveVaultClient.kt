package com.health.friday.desktop.sync.drive

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class DesktopDriveFile(
    val id: String,
    val name: String,
    val modifiedTime: String? = null
)

/** Google Drive v3 transport restricted to FRIDAY's app-created vault files. */
internal class DesktopDriveVaultClient {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile
    private var cachedFolderId: String? = null

    fun ensureVaultFolder(accessToken: String): String {
        require(accessToken.isNotBlank())
        cachedFolderId?.let { return it }

        val query = "name = '$VAULT_FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false"
        val existing = listAllFiles(accessToken, query).firstOrNull { it.name == VAULT_FOLDER_NAME }
        if (existing != null) {
            cachedFolderId = existing.id
            return existing.id
        }

        val metadata = buildJsonObject {
            put("name", VAULT_FOLDER_NAME)
            put("mimeType", FOLDER_MIME)
            put("appProperties", buildJsonObject { put("fridayApp", "FRIDAY") })
        }
        val response = request(
            accessToken = accessToken,
            method = "POST",
            url = "$DRIVE_API/files?fields=id,name",
            contentType = "application/json; charset=UTF-8",
            body = metadata.toString().toByteArray(StandardCharsets.UTF_8)
        )
        val folderId = json.parseToJsonElement(response).jsonObject["id"]
            ?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: throw IOException("Google Drive created FRIDAY_Vault without returning a folder ID.")
        cachedFolderId = folderId
        return folderId
    }

    fun uploadRecoveryEnvelope(
        accessToken: String,
        folderId: String,
        createdAtMs: Long,
        content: String,
        keyId: String
    ): String {
        val fileName = "$RECOVERY_FILE_PREFIX$createdAtMs$RECOVERY_FILE_SUFFIX"
        val metadata = buildJsonObject {
            put("name", fileName)
            put("mimeType", "application/json")
            put("parents", JsonArray(listOf(JsonPrimitive(folderId))))
            put(
                "appProperties",
                buildJsonObject {
                    put("fridayApp", "FRIDAY")
                    put("fridayKind", "key-envelope")
                    put("keyId", keyId)
                }
            )
        }
        return uploadMultipartJson(accessToken, metadata.toString(), content)
    }

    fun uploadBatch(
        accessToken: String,
        folderId: String,
        batchId: String,
        content: String
    ): String {
        require(batchId.isNotBlank())
        val metadata = buildJsonObject {
            put("name", "$BATCH_FILE_PREFIX$batchId$BATCH_FILE_SUFFIX")
            put("mimeType", "application/json")
            put("parents", JsonArray(listOf(JsonPrimitive(folderId))))
            put(
                "appProperties",
                buildJsonObject {
                    put("fridayApp", "FRIDAY")
                    put("fridayKind", "sync-batch")
                    put("batchId", batchId)
                }
            )
        }
        return uploadMultipartJson(accessToken, metadata.toString(), content)
    }

    fun findBatchFile(accessToken: String, folderId: String, batchId: String): DesktopDriveFile? {
        val name = "$BATCH_FILE_PREFIX$batchId$BATCH_FILE_SUFFIX"
        val query = "'$folderId' in parents and name = '${escapeQueryLiteral(name)}' and trashed = false"
        return listAllFiles(accessToken, query).firstOrNull { it.name == name }
    }

    fun listBatchFiles(accessToken: String, folderId: String): List<DesktopDriveFile> {
        val query = "'$folderId' in parents and trashed = false"
        return listAllFiles(accessToken, query)
            .filter { it.name.startsWith(BATCH_FILE_PREFIX) && it.name.endsWith(BATCH_FILE_SUFFIX) }
            .sortedBy { it.name }
    }

    fun findLatestRecoveryEnvelope(accessToken: String, folderId: String): DesktopDriveFile? {
        val query = "'$folderId' in parents and trashed = false"
        return listAllFiles(accessToken, query)
            .filter {
                it.name.startsWith(RECOVERY_FILE_PREFIX) &&
                    it.name.endsWith(RECOVERY_FILE_SUFFIX)
            }
            .maxByOrNull {
                it.name.removePrefix(RECOVERY_FILE_PREFIX)
                    .removeSuffix(RECOVERY_FILE_SUFFIX).toLongOrNull() ?: 0L
            }
    }

    fun downloadTextFile(accessToken: String, fileId: String): String {
        require(fileId.isNotBlank())
        return request(
            accessToken = accessToken,
            method = "GET",
            url = "$DRIVE_API/files/${encode(fileId)}?alt=media"
        )
    }

    private fun uploadMultipartJson(accessToken: String, metadataJson: String, content: String): String {
        val boundary = "friday_${UUID.randomUUID().toString().replace("-", "")}"
        val multipart = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson)
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(content)
            append("\r\n--$boundary--\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        try {
            val response = request(
                accessToken = accessToken,
                method = "POST",
                url = "$UPLOAD_API/files?uploadType=multipart&fields=id,name",
                contentType = "multipart/related; boundary=$boundary",
                body = multipart
            )
            return json.parseToJsonElement(response).jsonObject["id"]
                ?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: throw IOException("Google Drive upload returned no file ID.")
        } finally {
            multipart.fill(0)
        }
    }

    private fun listAllFiles(accessToken: String, query: String): List<DesktopDriveFile> {
        val result = mutableListOf<DesktopDriveFile>()
        val seenTokens = mutableSetOf<String>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$DRIVE_API/files?q=").append(encode(query))
                append("&spaces=drive&pageSize=1000")
                append("&fields=nextPageToken,files(id,name,modifiedTime)")
                pageToken?.let { append("&pageToken=").append(encode(it)) }
            }
            val page = json.parseToJsonElement(request(accessToken, "GET", url)).jsonObject
            val items = page["files"]?.jsonArray ?: JsonArray(emptyList())
            items.forEach { item ->
                val row = item.jsonObject
                val id = row["id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                val name = row["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                result += DesktopDriveFile(
                    id = id,
                    name = name,
                    modifiedTime = row["modifiedTime"]?.jsonPrimitive?.content
                )
            }
            val next = page["nextPageToken"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            if (next != null) check(seenTokens.add(next)) {
                "Google Drive repeated a page token; refusing an infinite listing loop."
            }
            pageToken = next
        } while (pageToken != null)
        return result
    }

    private fun request(
        accessToken: String,
        method: String,
        url: String,
        contentType: String? = null,
        body: ByteArray? = null
    ): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $accessToken")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (body != null) {
                doOutput = true
                setFixedLengthStreamingMode(body.size)
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    json.parseToJsonElement(response).jsonObject["error"]?.jsonObject
                        ?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                throw IOException("Google Drive $method failed (HTTP $code): ${message ?: response.take(800)}")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun escapeQueryLiteral(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        const val VAULT_FOLDER_NAME = "FRIDAY_Vault"
        const val RECOVERY_FILE_PREFIX = "friday_key_envelope_"
        const val RECOVERY_FILE_SUFFIX = ".json"
        const val BATCH_FILE_PREFIX = "batch_"
        const val BATCH_FILE_SUFFIX = ".friday"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val DRIVE_API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
