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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.health.friday.FridayApplication
import com.health.friday.ui.theme.FRIDAYTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DriveConnectionActivity : ComponentActivity() {
    private enum class DriveAction { BACKUP_KEY, RESTORE_KEY, SYNC }

    private var status by mutableStateOf("Encrypted Google Drive backup")
    private var busy by mutableStateOf(false)
    private var pendingAction: DriveAction? = null
    private var pendingPassphrase: CharArray? = null

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            clearPendingSecrets()
            busy = false
            status = "Google Drive authorization was cancelled."
            return@registerForActivityResult
        }

        try {
            val authorization = Identity.getAuthorizationClient(this)
                .getAuthorizationResultFromIntent(result.data)
            executePendingAction(authorization.accessToken)
        } catch (error: Exception) {
            clearPendingSecrets()
            busy = false
            status = "Google authorization failed: ${error.message}"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as FridayApplication).container
        if (container.cloudSyncSecurity.hasLocalMasterKey()) {
            status = "STATUS: CONNECTED & ENCRYPTED VAULT READY\n\nYour local master key is active and hardware-protected. Instant background sync is active across reboots and app updates."
        }

        setContent {
            FRIDAYTheme(darkTheme = true, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var recoveryPhrase by remember { mutableStateOf("") }
                    var confirmationPhrase by remember { mutableStateOf("") }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Google Drive — Encrypted Sync",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "The recovery phrase wraps FRIDAY's random master key. It is not uploaded to Google Drive and FRIDAY does not save it. Use the same phrase on your other devices.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedTextField(
                            value = recoveryPhrase,
                            onValueChange = { recoveryPhrase = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Recovery phrase (16+ characters)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )

                        OutlinedTextField(
                            value = confirmationPhrase,
                            onValueChange = { confirmationPhrase = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Confirm recovery phrase (for backup)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && recoveryPhrase.length >= 16 &&
                                recoveryPhrase == confirmationPhrase,
                            onClick = {
                                authorizeAndRun(DriveAction.BACKUP_KEY, recoveryPhrase)
                                recoveryPhrase = ""
                                confirmationPhrase = ""
                            }
                        ) { Text("Create / back up recovery key") }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy && recoveryPhrase.length >= 16,
                            onClick = {
                                authorizeAndRun(DriveAction.RESTORE_KEY, recoveryPhrase)
                                recoveryPhrase = ""
                                confirmationPhrase = ""
                            }
                        ) { Text("Restore key on this device") }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            onClick = { authorizeAndRun(DriveAction.SYNC, null) }
                        ) { Text("Sync now") }

                        if (busy) CircularProgressIndicator()

                        SelectionContainer {
                            Text(
                                text = status,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        }

                        Text(
                            "Keep the recovery phrase somewhere secure and independent of this phone. Anyone with the phrase and the encrypted Drive files can decrypt the backup. Losing the phrase means losing access to the cloud backup.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy,
                            onClick = { finish() }
                        ) { Text("Back") }
                    }
                }
            }
        }
    }

    private fun authorizeAndRun(action: DriveAction, phrase: String?) {
        pendingAction = action
        pendingPassphrase = phrase?.toCharArray()
        busy = true
        status = "Requesting Google Drive access..."

        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) {
                        clearPendingSecrets()
                        busy = false
                        status = "Google authorization returned no resolution intent."
                    } else {
                        authorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    }
                } else {
                    executePendingAction(result.accessToken)
                }
            }
            .addOnFailureListener { error ->
                clearPendingSecrets()
                busy = false
                status = "Google Drive authorization failed: ${error.message}"
            }
    }

    private fun executePendingAction(accessToken: String?) {
        val token = accessToken?.takeIf { it.isNotBlank() }
        if (token == null) {
            clearPendingSecrets()
            busy = false
            status = "Google did not return an access token."
            return
        }

        val action = pendingAction
        val phrase = pendingPassphrase
        if (action == null) {
            clearPendingSecrets()
            busy = false
            status = "No Drive operation was selected."
            return
        }

        lifecycleScope.launch {
            try {
                val container = (application as FridayApplication).container
                val message = withContext(Dispatchers.IO) {
                    when (action) {
                        DriveAction.BACKUP_KEY -> {
                            val recovery = requireNotNull(phrase) { "Enter a recovery phrase first." }
                            val envelope = container.driveSyncEngine.backUpRecoveryKey(token, recovery)
                            "RECOVERY KEY BACKED UP\nKey ID: ${envelope.keyId}\n\nThe encrypted recovery envelope is stored in FRIDAY_Vault. This device is ready to sync."
                        }
                        DriveAction.RESTORE_KEY -> {
                            val recovery = requireNotNull(phrase) { "Enter the recovery phrase used on the first device." }
                            val envelope = container.driveSyncEngine.restoreRecoveryKey(token, recovery)
                            "RECOVERY KEY RESTORED\nKey ID: ${envelope.keyId}\n\nThis device now has the same cloud encryption key."
                        }
                        DriveAction.SYNC -> {
                            val result = container.driveSyncEngine.performSync(token)
                            "SYNC COMPLETED\nPreviously existing records queued: ${result.backfilledRecords}\nUploaded batches: ${result.uploadedBatches}\nUploaded operations: ${result.uploadedOperations}\nDownloaded batches: ${result.downloadedBatches}\nApplied operations: ${result.appliedOperations}"
                        }
                    }
                }
                status = message
            } catch (error: Exception) {
                status = "DRIVE OPERATION FAILED\n${error.message ?: error.javaClass.simpleName}"
            } finally {
                phrase?.fill('\u0000')
                pendingPassphrase = null
                pendingAction = null
                busy = false
            }
        }
    }

    private fun clearPendingSecrets() {
        pendingPassphrase?.fill('\u0000')
        pendingPassphrase = null
        pendingAction = null
    }

    override fun onDestroy() {
        clearPendingSecrets()
        super.onDestroy()
    }

    companion object {
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
