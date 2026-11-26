package com.health.friday.sync.drive

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.health.friday.FridayApplication
import com.health.friday.sync.cloud.AndroidCloudSyncSecurity
import com.health.friday.sync.cloud.DriveSyncExecutionResult
import com.health.friday.sync.cloud.DriveSyncState
import com.health.friday.ui.theme.FRIDAYTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─── Colors ──────────────────────────────────────────────────────────────────
private val BgDark       = Color(0xFF0A0F1E)
private val Surface1     = Color(0xFF111827)
private val Surface2     = Color(0xFF1E293B)
private val AccentBlue   = Color(0xFF3B82F6)
private val AccentGreen  = Color(0xFF22C55E)
private val AccentAmber  = Color(0xFFF59E0B)
private val AccentRed    = Color(0xFFEF4444)
private val TextPrimary  = Color(0xFFF1F5F9)
private val TextSecond   = Color(0xFF94A3B8)
private val Divider      = Color(0xFF1E293B)

// ─── Screen states ───────────────────────────────────────────────────────────
private sealed interface DriveScreen {
    // User has nothing set up
    object NewUser : DriveScreen
    // OAuth done, no local key yet — are they first device or restoring?
    object ChoosePath : DriveScreen
    // Create new key: enter passphrase
    object CreateKey : DriveScreen
    // Restore from Drive: enter passphrase
    object RestoreKey : DriveScreen
    // All good
    data class Ready(val email: String, val lastSyncMs: Long) : DriveScreen
    // Auth expired
    data class AuthRequired(val email: String) : DriveScreen
    // Something went wrong
    data class Error(val message: String) : DriveScreen
    // Loading / in-progress
    data class Loading(val message: String) : DriveScreen
    // Delete confirmation
    object ConfirmDelete : DriveScreen
}

class DriveConnectionActivity : ComponentActivity() {

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            pendingTokenCallback?.invoke(null)
            pendingTokenCallback = null
            return@registerForActivityResult
        }
        try {
            val authorization = Identity.getAuthorizationClient(this)
                .getAuthorizationResultFromIntent(result.data)
            pendingTokenCallback?.invoke(authorization.accessToken)
        } catch (e: Exception) {
            pendingTokenCallback?.invoke(null)
        }
        pendingTokenCallback = null
    }

    private var pendingTokenCallback: ((String?) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FridayApplication).container

        setContent {
            FRIDAYTheme(darkTheme = true, dynamicColor = false) {
                val syncStatus by container.driveSyncStatusRepository.status.collectAsState()
                val hasMasterKey = container.cloudSyncSecurity.hasLocalMasterKey()

                // Derive initial screen
                val initial: DriveScreen = when {
                    syncStatus.state == DriveSyncState.AUTH_REQUIRED ->
                        DriveScreen.AuthRequired(email = "")
                    hasMasterKey ->
                        DriveScreen.Ready(email = "", lastSyncMs = syncStatus.lastSyncTimeMs)
                    else ->
                        DriveScreen.NewUser
                }

                var screen by remember { mutableStateOf<DriveScreen>(initial) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDark)
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    AnimatedContent(
                        targetState = screen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() }
                    ) { current ->
                        when (current) {
                            is DriveScreen.NewUser ->
                                NewUserScreen(
                                    onBack = { finish() },
                                    onConnect = { authorizeGoogle { token ->
                                        if (token == null) {
                                            screen = DriveScreen.Error("Google authorization cancelled.")
                                        } else {
                                            screen = if (hasMasterKey) DriveScreen.Ready("", syncStatus.lastSyncTimeMs)
                                                     else DriveScreen.ChoosePath
                                        }
                                    }}
                                )

                            is DriveScreen.ChoosePath ->
                                ChoosePathScreen(
                                    onFirstDevice = { screen = DriveScreen.CreateKey },
                                    onRestoring   = { screen = DriveScreen.RestoreKey },
                                    onBack        = { screen = DriveScreen.NewUser }
                                )

                            is DriveScreen.CreateKey ->
                                PassphraseScreen(
                                    title    = "Create your sync key",
                                    subtitle = "This key encrypts all your FRIDAY data before it leaves your phone. Write it down — FRIDAY cannot recover it for you.",
                                    cta      = "Create key & connect",
                                    confirm  = true,
                                    onBack   = { screen = DriveScreen.ChoosePath },
                                    onSubmit = { passphrase ->
                                        screen = DriveScreen.Loading("Creating key and backing up to Drive…")
                                        authorizeGoogle { token ->
                                            if (token == null) {
                                                screen = DriveScreen.Error("Google authorization failed.")
                                                return@authorizeGoogle
                                            }
                                            lifecycleScope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        container.driveSyncEngine.backUpRecoveryKey(token, passphrase)
                                                    }
                                                    container.driveSyncStatusRepository.clearAuthRequired()
                                                    screen = DriveScreen.Ready("", System.currentTimeMillis())
                                                } catch (e: Exception) {
                                                    screen = DriveScreen.Error(e.message ?: "Key creation failed.")
                                                } finally {
                                                    passphrase.fill('\u0000')
                                                }
                                            }
                                        }
                                    }
                                )

                            is DriveScreen.RestoreKey ->
                                PassphraseScreen(
                                    title    = "Restore your sync key",
                                    subtitle = "Enter the passphrase you created on your first device. FRIDAY will download the encrypted key from Drive.",
                                    cta      = "Restore & connect",
                                    confirm  = false,
                                    onBack   = { screen = DriveScreen.ChoosePath },
                                    onSubmit = { passphrase ->
                                        screen = DriveScreen.Loading("Restoring key from Drive…")
                                        authorizeGoogle { token ->
                                            if (token == null) {
                                                screen = DriveScreen.Error("Google authorization failed.")
                                                return@authorizeGoogle
                                            }
                                            lifecycleScope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        container.driveSyncEngine.restoreRecoveryKey(token, passphrase)
                                                    }
                                                    container.driveSyncStatusRepository.clearAuthRequired()
                                                    screen = DriveScreen.Ready("", System.currentTimeMillis())
                                                } catch (e: Exception) {
                                                    screen = DriveScreen.Error(e.message ?: "Key restore failed. Check your passphrase.")
                                                } finally {
                                                    passphrase.fill('\u0000')
                                                }
                                            }
                                        }
                                    }
                                )

                            is DriveScreen.Ready ->
                                ReadyScreen(
                                    lastSyncMs  = current.lastSyncMs,
                                    syncState   = syncStatus.state,
                                    onSyncNow   = {
                                        screen = DriveScreen.Loading("Syncing…")
                                        authorizeGoogle { token ->
                                            if (token == null) { screen = DriveScreen.AuthRequired(""); return@authorizeGoogle }
                                            lifecycleScope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    container.driveSyncEngine.performSyncWithAccessToken(this@DriveConnectionActivity, token)
                                                }
                                                screen = when (result) {
                                                    is DriveSyncExecutionResult.Success ->
                                                        DriveScreen.Ready("", System.currentTimeMillis())
                                                    is DriveSyncExecutionResult.AuthRequired ->
                                                        DriveScreen.AuthRequired("")
                                                    else ->
                                                        DriveScreen.Error("Sync failed. Check your connection.")
                                                }
                                            }
                                        }
                                    },
                                    onDelete    = { screen = DriveScreen.ConfirmDelete },
                                    onBack      = { finish() }
                                )

                            is DriveScreen.AuthRequired ->
                                ReconnectScreen(
                                    onReconnect = {
                                        screen = DriveScreen.Loading("Reconnecting…")
                                        authorizeGoogle { token ->
                                            if (token == null) { screen = DriveScreen.Error("Authorization cancelled."); return@authorizeGoogle }
                                            lifecycleScope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    container.driveSyncEngine.performSyncWithAccessToken(this@DriveConnectionActivity, token)
                                                }
                                                screen = when (result) {
                                                    is DriveSyncExecutionResult.Success -> DriveScreen.Ready("", System.currentTimeMillis())
                                                    else -> DriveScreen.Error("Reconnect failed.")
                                                }
                                            }
                                        }
                                    },
                                    onBack = { finish() }
                                )

                            is DriveScreen.Loading ->
                                LoadingScreen(message = current.message)

                            is DriveScreen.Error ->
                                ErrorScreen(
                                    message = current.message,
                                    onRetry = { screen = if (hasMasterKey) DriveScreen.Ready("", syncStatus.lastSyncTimeMs) else DriveScreen.NewUser },
                                    onBack  = { finish() }
                                )

                            is DriveScreen.ConfirmDelete ->
                                DeleteScreen(
                                    onDeleteKeyOnly = {
                                        screen = DriveScreen.Loading("Removing local key…")
                                        lifecycleScope.launch {
                                            withContext(Dispatchers.IO) {
                                                container.cloudSyncSecurity.deleteLocalKey()
                                            }
                                            screen = DriveScreen.NewUser
                                        }
                                    },
                                    onDeleteAll = {
                                        screen = DriveScreen.Loading("Deleting all Drive data…")
                                        authorizeGoogle { token ->
                                            if (token == null) { screen = DriveScreen.Error("Authorization required to delete Drive data."); return@authorizeGoogle }
                                            lifecycleScope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        container.driveSyncEngine.deleteVaultAndLocalKey(token)
                                                    }
                                                } catch (e: Exception) {
                                                    // best-effort — still wipe local key
                                                    container.cloudSyncSecurity.deleteLocalKey()
                                                }
                                                screen = DriveScreen.NewUser
                                            }
                                        }
                                    },
                                    onCancel = { screen = DriveScreen.Ready("", syncStatus.lastSyncTimeMs) }
                                )
                        }
                    }
                }
            }
        }
    }

    private fun authorizeGoogle(onResult: (token: String?) -> Unit) {
        pendingTokenCallback = onResult
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pi = result.pendingIntent
                    if (pi == null) { onResult(null); pendingTokenCallback = null }
                    else authorizationLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                } else {
                    pendingTokenCallback = null
                    onResult(result.accessToken?.takeIf { it.isNotBlank() })
                }
            }
            .addOnFailureListener { pendingTokenCallback = null; onResult(null) }
    }

    companion object {
        private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}

// ─── Screens ─────────────────────────────────────────────────────────────────

@Composable
private fun ScreenShell(
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextSecond)
            }
        } else Spacer(Modifier.height(56.dp))
        content()
    }
}

@Composable
private fun NewUserScreen(onBack: () -> Unit, onConnect: () -> Unit) {
    ScreenShell(onBack = onBack) {
        Spacer(Modifier.height(32.dp))
        // Google-style account avatar placeholder
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Surface2)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            Text("G", color = AccentBlue, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Connect Google Drive",
            color = TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "FRIDAY keeps your data encrypted end-to-end. Only you can read it.",
            color = TextSecond,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(40.dp))
        FridayPrimaryButton(label = "Sign in with Google", onClick = onConnect)
    }
}

@Composable
private fun ChoosePathScreen(onFirstDevice: () -> Unit, onRestoring: () -> Unit, onBack: () -> Unit) {
    ScreenShell(onBack = onBack) {
        Spacer(Modifier.height(32.dp))
        Text("Which device is this?", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Your Drive is connected. Now tell FRIDAY whether to create a new encryption key or restore one.", color = TextSecond, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))
        PathCard(
            title    = "My first device",
            subtitle = "Create a new encryption key and back it up to Drive.",
            onClick  = onFirstDevice
        )
        Spacer(Modifier.height(12.dp))
        PathCard(
            title    = "I'm adding a second device",
            subtitle = "Restore the key I created on my other device.",
            onClick  = onRestoring
        )
    }
}

@Composable
private fun PathCard(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .border(1.dp, Divider, RoundedCornerShape(16.dp)),
        color = Surface1
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecond, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PassphraseScreen(
    title: String,
    subtitle: String,
    cta: String,
    confirm: Boolean,
    onBack: () -> Unit,
    onSubmit: (CharArray) -> Unit
) {
    var key     by remember { mutableStateOf("") }
    var conf    by remember { mutableStateOf("") }
    var showKey  by remember { mutableStateOf(false) }
    var showConf by remember { mutableStateOf(false) }

    val minLen = AndroidCloudSyncSecurity.MIN_RECOVERY_PASSPHRASE_LENGTH
    val keyOk  = key.length >= minLen
    val confOk = !confirm || key == conf
    val canGo  = keyOk && confOk

    ScreenShell(onBack = onBack) {
        Spacer(Modifier.height(24.dp))
        Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = TextSecond, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(32.dp))

        FridayTextField(
            value         = key,
            onValueChange = { key = it },
            label         = "Passphrase (min $minLen characters)",
            visible       = showKey,
            onToggle      = { showKey = !showKey }
        )

        if (confirm) {
            Spacer(Modifier.height(12.dp))
            FridayTextField(
                value         = conf,
                onValueChange = { conf = it },
                label         = "Confirm passphrase",
                visible       = showConf,
                onToggle      = { showConf = !showConf },
                isError       = conf.isNotEmpty() && key != conf,
                errorText     = "Passphrases don't match"
            )
        }

        Spacer(Modifier.height(32.dp))
        FridayPrimaryButton(
            label   = cta,
            enabled = canGo,
            onClick = { onSubmit(key.toCharArray()) }
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Write this down. FRIDAY cannot recover your passphrase.",
            color = TextSecond,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ReadyScreen(
    lastSyncMs: Long,
    syncState: DriveSyncState,
    onSyncNow: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    val syncLabel = when {
        lastSyncMs == 0L -> "Never synced"
        else -> {
            val diff = System.currentTimeMillis() - lastSyncMs
            when {
                diff < 60_000       -> "Synced just now"
                diff < 3_600_000    -> "Synced ${diff / 60_000}m ago"
                else                -> "Synced ${diff / 3_600_000}h ago"
            }
        }
    }
    val isSyncing = syncState == DriveSyncState.SYNCING

    ScreenShell(onBack = onBack) {
        Spacer(Modifier.height(32.dp))
        // Status pill
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(AccentGreen.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
            Text("Drive connected", color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(Modifier.height(24.dp))
        Text(syncLabel, color = TextSecond, fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(40.dp))

        FridayPrimaryButton(
            label   = if (isSyncing) "Syncing…" else "Sync now",
            enabled = !isSyncing,
            onClick = onSyncNow
        )
        if (isSyncing) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentBlue)
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = Divider)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDelete)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(20.dp))
            Column {
                Text("Remove sync data", color = AccentRed, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Delete local key or wipe Drive vault", color = TextSecond, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReconnectScreen(onReconnect: () -> Unit, onBack: () -> Unit) {
    ScreenShell(onBack = onBack) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber,
            modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Text("Reconnect required", color = TextPrimary, fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Text("Your Google Drive authorization has expired. Sign in again to resume syncing.",
            color = TextSecond, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(40.dp))
        FridayPrimaryButton(label = "Sign in with Google", onClick = onReconnect)
    }
}

@Composable
private fun LoadingScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = AccentBlue)
            Text(message, color = TextSecond, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    ScreenShell(onBack = onBack) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentRed,
            modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Text("Something went wrong", color = TextPrimary, fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Text(message, color = TextSecond, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(32.dp))
        FridayPrimaryButton(label = "Try again", onClick = onRetry)
    }
}

@Composable
private fun DeleteScreen(
    onDeleteKeyOnly: () -> Unit,
    onDeleteAll: () -> Unit,
    onCancel: () -> Unit
) {
    ScreenShell {
        Spacer(Modifier.height(32.dp))
        Text("Remove sync data", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Choose what to remove. This cannot be undone.", color = TextSecond, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))

        // Option A — key only
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onDeleteKeyOnly)
                .border(1.dp, Divider, RoundedCornerShape(16.dp)),
            color = Surface1
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Remove local key only", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Deletes the key from this device. Your Drive data stays intact — you can restore access with your passphrase.",
                    color = TextSecond, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Option B — wipe everything
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onDeleteAll)
                .border(1.dp, AccentRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            color = Surface1
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Delete everything", color = AccentRed, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Removes the local key AND all encrypted batch files from Google Drive. Your data cannot be recovered.",
                    color = TextSecond, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Cancel", color = TextSecond)
        }
    }
}

// ─── Shared components ────────────────────────────────────────────────────────

@Composable
private fun FridayPrimaryButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentBlue,
            disabledContainerColor = Surface2
        )
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FridayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
    isError: Boolean = false,
    errorText: String = ""
) {
    Column {
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            modifier      = Modifier.fillMaxWidth(),
            label         = { Text(label, fontSize = 13.sp) },
            singleLine    = true,
            isError       = isError,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions      = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) "Hide" else "Show",
                        tint = TextSecond
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor       = TextPrimary,
                unfocusedTextColor     = TextPrimary,
                focusedBorderColor     = AccentBlue,
                unfocusedBorderColor   = Divider,
                errorBorderColor       = AccentRed,
                focusedContainerColor  = Surface1,
                unfocusedContainerColor= Surface1,
                focusedLabelColor      = AccentBlue,
                unfocusedLabelColor    = TextSecond
            )
        )
        if (isError && errorText.isNotEmpty()) {
            Text(errorText, color = AccentRed, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
        }
    }
}
