package com.health.friday.sync.cloud

import com.health.friday.shared.sync.cloud.CloudSyncStore
import com.health.friday.shared.sync.cloud.EncryptedSyncPayload
import com.health.friday.shared.sync.cloud.SyncBatch
import com.health.friday.shared.sync.cloud.SyncCursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

data class DriveSyncResult(
    val uploadedBatches: Int,
    val uploadedOperations: Int,
    val downloadedBatches: Int,
    val appliedOperations: Int
)

class AndroidDriveSyncEngine(
    private val cloudSyncStore: CloudSyncStore,
    private val security: AndroidCloudSyncSecurity,
    private val deviceId: String
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun performSync(accessToken: String): DriveSyncResult = withContext(Dispatchers.IO) {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }

        val folderId = getOrCreateBackupFolder(accessToken)

        // 1. Upload local outbox operations as encrypted batches
        val uploadResult = uploadPendingOutbox(accessToken, folderId)

        // 2. Download remote encrypted batches and apply them locally
        val downloadResult = downloadRemoteBatches(accessToken, folderId)

        DriveSyncResult(
            uploadedBatches = uploadResult.first,
            uploadedOperations = uploadResult.second,
            downloadedBatches = downloadResult.first,
            appliedOperations = downloadResult.second
        )
    }

    private suspend fun uploadPendingOutbox(
        accessToken: String,
        folderId: String
    ): Pair<Int, Int> {
        val pendingOps = cloudSyncStore.pendingOutbox(limit = 200)
        if (pendingOps.isEmpty()) return 0 to 0

        val nowMs = System.currentTimeMillis()
        val batch = SyncBatch.create(
            deviceId = deviceId,
            createdAtMs = nowMs,
            operations = pendingOps
        )

        val encryptedPayload = security.encryptBatch(batch)
        val payloadJson = json.encodeToString(EncryptedSyncPayload.serializer(), encryptedPayload)

        val fileName = "batch_${nowMs}_$deviceId.friday"
        val fileId = uploadFileToDrive(accessToken, folderId, fileName, payloadJson)

        check(!fileId.isNullOrBlank()) {
            "Google Drive returned an empty file ID during upload"
        }

        cloudSyncStore.markUploaded(
            operationIds = pendingOps.map { it.operationId },
            batchId = batch.batchId,
            uploadedAtMs = nowMs
        )

        return 1 to pendingOps.size
    }

    private suspend fun downloadRemoteBatches(
        accessToken: String,
        folderId: String
    ): Pair<Int, Int> {
        val currentCursor = cloudSyncStore.readCursor()
        val files = listBackupFiles(accessToken, folderId)

        var downloadedBatches = 0
        var appliedOps = 0

        for (file in files) {
            val fileId = file.id
            val fileName = file.name
            if (fileId.isBlank() || !fileName.endsWith(".friday")) continue

            val jsonContent = downloadFileFromDrive(accessToken, fileId) ?: continue
            val payload = try {
                json.decodeFromString(EncryptedSyncPayload.serializer(), jsonContent)
            } catch (_: Exception) {
                continue
            }

            val batch = try {
                security.decryptBatch(payload)
            } catch (_: Exception) {
                continue
            }

            val updatedCursor = SyncCursor(
                drivePageToken = currentCursor.drivePageToken,
                lastSuccessfulSyncAtMs = System.currentTimeMillis()
            )

            val remoteResult = cloudSyncStore.ingestRemoteBatch(batch, updatedCursor)

            downloadedBatches++
            appliedOps += remoteResult.appliedOperations
        }

        return downloadedBatches to appliedOps
    }

    private fun getOrCreateBackupFolder(accessToken: String): String {
        val query = "name = 'FRIDAY_Vault' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val searchUrl = "https://www.googleapis.com/drive/v3/files?q=${URLParamEncoder.encode(query)}"

        val conn = (URL(searchUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        val code = conn.responseCode
        if (code == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val existingId = parseFirstFileId(body)
            if (!existingId.isNullOrBlank()) return existingId
        }

        // Create folder
        val createUrl = "https://www.googleapis.com/drive/v3/files"
        val body = """{"name":"FRIDAY_Vault","mimeType":"application/vnd.google-apps.folder"}"""

        val createConn = (URL(createUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            doOutput = true
            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }

        val createCode = createConn.responseCode
        check(createCode in 200..299) {
            "Failed to create FRIDAY_Vault folder on Google Drive (HTTP $createCode)"
        }

        val responseText = createConn.inputStream.bufferedReader().use { it.readText() }
        val folderId = parseFirstFileId(responseText)
        check(!folderId.isNullOrBlank()) { "Google Drive returned an empty folder ID" }
        return folderId
    }

    private fun uploadFileToDrive(
        accessToken: String,
        folderId: String,
        fileName: String,
        content: String
    ): String? {
        val url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val boundary = "======FRIDAY_SYNC_BOUNDARY======"

        val metadataJson = """{"name":"$fileName","parents":["$folderId"]}"""

        val body = StringBuilder().apply {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadataJson)
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
            append(content)
            append("\r\n--$boundary--\r\n")
        }.toString()

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            doOutput = true
            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            return parseFirstFileId(responseText)
        }
        return null
    }

    private fun downloadFileFromDrive(accessToken: String, fileId: String): String? {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        if (conn.responseCode == 200) {
            return conn.inputStream.bufferedReader().use { it.readText() }
        }
        return null
    }

    private data class DriveFileItem(val id: String, val name: String)

    private fun listBackupFiles(accessToken: String, folderId: String): List<DriveFileItem> {
        val query = "'$folderId' in parents and trashed = false"
        val url = "https://www.googleapis.com/drive/v3/files?q=${URLParamEncoder.encode(query)}&fields=files(id,name)"

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }

        if (conn.responseCode == 200) {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parseFileList(body)
        }
        return emptyList()
    }

    private fun parseFirstFileId(jsonText: String): String? {
        val idRegex = """"id"\s*:\s*"([^"]+)"""".toRegex()
        return idRegex.find(jsonText)?.groupValues?.get(1)
    }

    private fun parseFileList(jsonText: String): List<DriveFileItem> {
        val itemRegex = """"id"\s*:\s*"([^"]+)"\s*,\s*"name"\s*:\s*"([^"]+)"""".toRegex()
        val results = mutableListOf<DriveFileItem>()
        itemRegex.findAll(jsonText).forEach { match ->
            val id = match.groupValues[1]
            val name = match.groupValues[2]
            results.add(DriveFileItem(id, name))
        }
        return results
    }

    private object URLParamEncoder {
        fun encode(input: String): String = java.net.URLEncoder.encode(input, "UTF-8")
    }
}
