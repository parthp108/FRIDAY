package com.health.friday.sync.drive

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.health.friday.ui.theme.FRIDAYTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriveConnectionActivity : ComponentActivity() {

    companion object {
        private const val DRIVE_FILE_SCOPE =
            "https://www.googleapis.com/auth/drive.file"

        private const val DRIVE_API =
            "https://www.googleapis.com/drive/v3/files"

        private const val FOLDER_MIME =
            "application/vnd.google-apps.folder"
    }

    private val httpClient = OkHttpClient()

    private var status by mutableStateOf(
        "FRIDAY Google Drive connection test"
    )

    private var busy by mutableStateOf(false)

    private val authorizationLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->

            if (result.resultCode != Activity.RESULT_OK) {
                status = "Google Drive authorization cancelled."
                return@registerForActivityResult
            }

            try {
                val authorizationResult =
                    Identity.getAuthorizationClient(this)
                        .getAuthorizationResultFromIntent(result.data)

                runDriveTest(authorizationResult.accessToken)

            } catch (e: Exception) {
                status = "Authorization failed: ${e.message}"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FRIDAYTheme(
                darkTheme = true,
                dynamicColor = false
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Google Drive — Stage 1",
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            SelectionContainer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = status,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (busy) {
                            CircularProgressIndicator()
                        }

                        Button(
                            onClick = { authorizeAndTest() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Connect and run test")
                        }

                        Button(
                            onClick = { finish() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }

    private fun authorizeAndTest() {
        status = "Requesting Google Drive access..."

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(
                listOf(Scope(DRIVE_FILE_SCOPE))
            )
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->

                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent

                    if (pendingIntent == null) {
                        status = "Google authorization returned no resolution."
                        return@addOnSuccessListener
                    }

                    authorizationLauncher.launch(
                        IntentSenderRequest.Builder(
                            pendingIntent.intentSender
                        ).build()
                    )
                } else {
                    runDriveTest(result.accessToken)
                }
            }
            .addOnFailureListener { error ->
                status = "Authorization error: ${error.message}"
            }
    }

    private fun runDriveTest(token: String?) {
        if (token.isNullOrBlank()) {
            status = "Google did not return an access token."
            return
        }

        busy = true
        status = "Connecting to Google Drive..."

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    performRoundTrip(token)
                }

                status = result

            } catch (e: Exception) {
                status = "Drive test failed:\n${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun performRoundTrip(token: String): String {

        // 1. Find or create FRIDAY's own Drive folder.
        val folderId = findOrCreateFridayFolder(token)

        // 2. Generate test content.
        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss",
            Locale.US
        ).format(Date())

        val originalContent = """
            FRIDAY Google Drive Stage 1 test
            Device: Android
            Created: $timestamp
            Round-trip verification: expected
        """.trimIndent()

        // 3. Upload test file.
        val fileId = uploadTestFile(
            token = token,
            folderId = folderId,
            content = originalContent
        )

        // 4. Download uploaded file.
        val downloadedContent = downloadFile(
            token = token,
            fileId = fileId
        )

        // 5. Verify exact content.
        if (downloadedContent != originalContent) {
            throw IOException(
                "Downloaded file content does not match uploaded content."
            )
        }

        return """
            DRIVE TEST PASSED

            Authorization: successful
            Folder: FRIDAY
            Folder ID: $folderId

            Upload: successful
            Download: successful
            Content comparison: passed

            File ID: $fileId
        """.trimIndent()
    }

    private fun findOrCreateFridayFolder(token: String): String {

        val query =
            "appProperties has { key='fridayApp' and value='FRIDAY' } " +
            "and mimeType='$FOLDER_MIME' and trashed=false"

        val encodedQuery = URLEncoder.encode(
            query,
            StandardCharsets.UTF_8.name()
        )

        val url =
            "$DRIVE_API?q=$encodedQuery&spaces=drive&fields=files(id,name)"

        val response = execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
        )

        val files = JSONObject(response)
            .optJSONArray("files") ?: JSONArray()

        if (files.length() > 0) {
            return files.getJSONObject(0).getString("id")
        }

        val metadata = JSONObject()
            .put("name", "FRIDAY")
            .put("mimeType", FOLDER_MIME)
            .put(
                "appProperties",
                JSONObject().put("fridayApp", "FRIDAY")
            )

        val body = metadata.toString().toRequestBody(
            "application/json; charset=utf-8".toMediaType()
        )

        val createResponse = execute(
            Request.Builder()
                .url("$DRIVE_API?fields=id,name")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
        )

        return JSONObject(createResponse).getString("id")
    }

    private fun uploadTestFile(
        token: String,
        folderId: String,
        content: String
    ): String {

        val metadata = JSONObject()
            .put(
                "name",
                "stage1-android-${System.currentTimeMillis()}.txt"
            )
            .put("mimeType", "text/plain")
            .put("parents", JSONArray().put(folderId))

        val multipartBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(
                metadata.toString().toRequestBody(
                    "application/json; charset=UTF-8".toMediaType()
                )
            )
            .addPart(
                content.toRequestBody(
                    "text/plain; charset=UTF-8".toMediaType()
                )
            )
            .build()

        val response = execute(
            Request.Builder()
                .url(
                    "https://www.googleapis.com/upload/drive/v3/files" +
                    "?uploadType=multipart&fields=id,name"
                )
                .header("Authorization", "Bearer $token")
                .post(multipartBody)
                .build()
        )

        return JSONObject(response).getString("id")
    }

    private fun downloadFile(
        token: String,
        fileId: String
    ): String {

        val request = Request.Builder()
            .url("$DRIVE_API/$fileId?alt=media")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        return execute(request)
    }

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->

            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                throw IOException(
                    "HTTP ${response.code}:\n$body"
                )
            }

            return body
        }
    }
}
