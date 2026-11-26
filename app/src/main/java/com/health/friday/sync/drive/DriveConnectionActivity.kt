package com.health.friday.sync.drive

import android.accounts.AccountManager
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwitchAccount
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
    object CreateKey : DriveScreen
    object RestoreKey : DriveScreen
    data class Ready(val email: String, val lastSyncMs: Long) : DriveScreen
    data class AuthRequired(val email: String) : DriveScreen
    data class Error(val message: String) : DriveScreen
    data class Loading(val message: String) : DriveScreen
    object ConfirmDelete : DriveScreen
}

class DriveConnectionActivity : ComponentActivity() {

    private var pendingAuthCallback: ((email: String?, token: String?) -> Unit)? = null

    private val authorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val callback = pendingAuthCallback
        pendingAuthCallback = null

        if (result.resultCode != Activity.RESULT_OK) {
            callback?.invoke(null, null)
            return@registerForActivityResult
        }
        try {
            val authorization = Identity.getAuthorizationClient(this)
                .getAuthorizationResultFromIntent(result.data)
            val accounts = AccountManager.get(this).getAccountsByType("com.google")
            val email = accounts.firstOrNull()?.name ?: "Google Account Connected"
            callback?.invoke(email, authorization.accessToken)
        } catch (e: Exception) {
            callback?.invoke(null, null)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as FridayApplication).container

        setContent {
            FRIDAYTheme(darkTheme = true, dynamicColor = false) {
                val syncStatus by container.driveSyncStatusRepository.status.collectAsState()
                val hasMasterKey = container.cloudSyncSecurity.hasLocalMasterKey()

                val initial: DriveScreen = when {
                    syncStatus.state == DriveSyncState.AUTH_REQUIRED ->
                        DriveScreen.AuthRequired(email = syncStatus.accountEmail.orEmpty())
                    hasMasterKey ->
                        DriveScreen.Ready(
                            email = syncStatus.accountEmail.orEmpty(),
                            lastSyncMs = syncStatus.lastSyncTimeMs
                        )
                    else ->
                        DriveScreen.CreateKey
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
                            is DriveScreen.CreateKey ->
                                PassphraseScreen(
                                    title    = "Set up Google Drive Sync",
                                    subtitle = "Create your 8+ character recovery passphrase to encrypt your FRIDAY health data end-to-end.",
                                    cta      = "Continue & Connect Google Account",
                                    confirm  = true,
                                    onSwitchToRestore = { screen = DriveScreen.RestoreKey },
                                    onBack   = { finish() },
                                    onSubmit = { passphrase ->
                                        screen = DriveScreen.Loading("Opening Google Account Chooser...")
                                        authorizeGoogle { email, token ->
                                            if (token == null) {
                                                screen = DriveScreen.Error("Google account selection was cancelled or failed.")
                                                return@authorizeGoogle
                                            }
                                            val accountEmail = email ?: "Google Account Connected"
                                            screen = DriveScreen.Loading("Creating key & backing up to Drive...")
                                            lifecycleScope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        container.driveSyncEngine.backUpRecoveryKey(token, passphrase)
                                                    }
                                                    container.driveSyncStatusRepository.updateStatus(
                                                        state = DriveSyncState.SUCCESS,
                                                        accountEmail = accountEmail
                                                    )
                                                    screen = DriveScreen.Ready(accountEmail, System.currentTimeMillis())
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
                                    title    = "Restore existing key",
                                    subtitle = "Enter the 8+ character passphrase created on your first device to decrypt and connect your backup.",
                                    cta      = "Restore & Connect Google Account",
                                    confirm  = false,
                                    onSwitchToRestore = null,
                                    onBack   = { screen = DriveScreen.CreateKey },
                                    onSubmit = { passphrase ->
                                        screen = DriveScreen.Loading("Opening Google Account Chooser...")
                                        authorizeGoogle { email, token ->
                                            if (token == null) {
                                                screen = DriveScreen.Error("Google account selection was cancelled or failed.")
                                                return@authorizeGoogle
                                            }
                                            val accountEmail = email ?: "Google Account Connected"
                                            screen = DriveScreen.Loading("Restoring key from Drive...")
                                            lifecycleScope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) {
                                                        container.driveSyncEngine.restoreRecoveryKey(token, passphrase)
                                                    }
                                                    container.driveSyncStatusRepository.updateStatus(
                                                        state = DriveSyncState.SUCCESS,
                                                        accountEmail = accountEmail
                                                    )
                                                    screen = DriveScreen.Ready(accountEmail, System.currentTimeMillis())
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
                                    email       = current.email.ifBlank { syncStatus.accountEmail ?: "Google Account Connected" },
                                    lastSyncMs  = current.lastSyncMs.takeIf { it > 0 } ?: syncStatus.lastSyncTimeMs,
                                    syncState   = syncStatus.state,
                                    onSyncNow   = {
                                        screen = DriveScreen.Loading("Syncing...")
                                        authorizeGoogle { email, token ->
                                            if (token == null) { screen = DriveScreen.AuthRequired(current.email); return@authorizeGoogle }
                                            val accountEmail = email ?: current.email
                                            lifecycleScope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    container.driveSyncEngine.performSyncWithAccessToken(this@DriveConnectionActivity, token)
                                                }
                                                screen = when (result) {
                                                    is DriveSyncExecutionResult.Success -> {
                                                        container.driveSyncStatusRepository.updateStatus(DriveSyncState.SUCCESS, accountEmail = accountEmail)
                                                        DriveScreen.Ready(accountEmail, System.currentTimeMillis())
                                                    }
                                                    is DriveSyncExecutionResult.AuthRequired ->
                                                        DriveScreen.AuthRequired(accountEmail)
                                                    else ->
                                                        DriveScreen.Error("Sync failed. Check network connection.")
                                                }
                                            }
                                        }
                                    },
                                    onSwitchAccount = {
                                        screen = DriveScreen.Loading("Opening Google Account Chooser...")
                                        authorizeGoogle { email, token ->
                                            if (email != null && token != null) {
                                                container.driveSyncStatusRepository.updateStatus(DriveSyncState.SUCCESS, accountEmail = email)
                                                screen = DriveScreen.Ready(email, System.currentTimeMillis())
                                            } else {
                                                screen = DriveScreen.Ready(current.email, current.lastSyncMs)
                                            }
                                        }
                                    },
                                    onDelete = { screen = DriveScreen.ConfirmDelete },
                                    onBack   = { finish() }
                                )

                            is DriveScreen.AuthRequired ->
                                ReconnectScreen(
                                    email = current.email.ifBlank { "Google Account" },
                                    onReconnect = {
                                        screen = DriveScreen.Loading("Opening Google Account Chooser...")
                                        authorizeGoogle { email, token ->
                                            if (token == null) { screen = DriveScreen.Error("Authorization cancelled."); return@authorizeGoogle }
                                            val accountEmail = email ?: current.email
                                            lifecycleScope.launch {
                                                val result = withContext(Dispatchers.IO) {
                                                    container.driveSyncEngine.performSyncWithAccessToken(this@DriveConnectionActivity, token)
                                                }
                                                screen = when (result) {
                                                    is DriveSyncExecutionResult.Success -> {
                                                        container.driveSyncStatusRepository.updateStatus(DriveSyncState.SUCCESS, accountEmail = accountEmail)
                                                        DriveScreen.Ready(accountEmail, System.currentTimeMillis())
                                                    }
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
                                    onRetry = { screen = if (hasMasterKey) DriveScreen.Ready("", syncStatus.lastSyncTimeMs) else DriveScreen.CreateKey },
                                    onBack  = { finish() }
                                )

                            is DriveScreen.ConfirmDelete ->
                                DeleteScreen(
                                    onDeleteKeyOnly = {
                                        screen = DriveScreen.Loading("Removing local key...")
                                        lifecycleScope.launch {
                                            withContext(Dispatchers.IO) {
                                                container.cloudSyncSecurity.deleteLocalKey()
                                                container.driveSyncStatusRepository.clearAll()
                                            }
                                            screen = DriveScreen.CreateKey
                                        }
                                    },
                                    onDeleteAll = {
                                        screen = DriveScreen.Loading("Deleting all Drive data...")
                                        authorizeGoogle { _, token ->
                                            lifecycleScope.launch {
                                                try {
                                                    if (token != null) {
                                                        withContext(Dispatchers.IO) {
                                                            container.driveSyncEngine.deleteVaultAndLocalKey(token)
                                                        }
                                                    } else {
                                                        container.cloudSyncSecurity.deleteLocalKey()
                                                    }
                                                } catch (_: Exception) {
                                                    container.cloudSyncSecurity.deleteLocalKey()
                                                } finally {
                                                    container.driveSyncStatusRepository.clearAll()
                                                }
                                                screen = DriveScreen.CreateKey
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

    private fun authorizeGoogle(onResult: (email: String?, token: String?) -> Unit) {
        pendingAuthCallback = onResult
        val accounts = AccountManager.get(this).getAccountsByType("com.google")
        val primaryAccount = accounts.firstOrNull()
        val accountName = primaryAccount?.name ?: "Google Account Connected"

        val requestBuilder = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))

        if (primaryAccount != null) {
            requestBuilder.setAccount(primaryAccount)
        }

        val request = requestBuilder.build()

        Identity.getAuthorizationClient(this)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pi = result.pendingIntent
                    if (pi == null) {
                        val callback = pendingAuthCallback
                        pendingAuthCallback = null
                        callback?.invoke(null, null)
                    } else {
                        authorizationLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    }
                } else {
                    val token = result.accessToken?.takeIf { it.isNotBlank() }
                    val callback = pendingAuthCallback
                    pendingAuthCallback = null
                    callback?.invoke(accountName, token)
                }
            }
            .addOnFailureListener {
                val callback = pendingAuthCallback
                pendingAuthCallback = null
                callback?.invoke(null, null)
            }
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
private fun PassphraseScreen(
    title: String,
    subtitle: String,
    cta: String,
    confirm: Boolean,
    onSwitchToRestore: (() -> Unit)?,
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
        Spacer(Modifier.height(16.dp))
        Text(title, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = TextSecond, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(28.dp))

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

        Spacer(Modifier.height(28.dp))
        FridayPrimaryButton(
            label   = cta,
            enabled = canGo,
            onClick = { onSubmit(key.toCharArray()) }
        )

        if (onSwitchToRestore != null) {
            Spacer(Modifier.height(12.dp))
            TextButton(
                onClick = onSwitchToRestore,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Already have a key? Restore from Drive", color = AccentBlue, fontSize = 13.sp)
            }
        }

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
    email: String,
    lastSyncMs: Long,
    syncState: DriveSyncState,
    onSyncNow: () -> Unit,
    onSwitchAccount: () -> Unit,
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
        Spacer(Modifier.height(24.dp))
        Text("Drive Sync Active", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Google Play Store style Account Card
        Surface(
            color = Surface1,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(18.dp))
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AccentBlue.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = email.take(1).uppercase().ifBlank { "G" },
                            color = AccentBlue,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = email.ifBlank { "Google Account Connected" },
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(8.dp).background(AccentGreen, CircleShape))
                            Text("Connected & Vault Ready", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                HorizontalDivider(color = Divider)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Last Sync", color = TextSecond, fontSize = 13.sp)
                    Text(syncLabel, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        FridayPrimaryButton(
            label   = if (isSyncing) "Syncing..." else "Sync Now",
            enabled = !isSyncing,
            onClick = onSyncNow
        )

        if (isSyncing) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentBlue)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = onSwitchAccount,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
        ) {
            Icon(Icons.Default.SwitchAccount, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Switch / Connect Another Google Account", fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))
        HorizontalDivider(color = Divider)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onDelete)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = AccentRed, modifier = Modifier.size(20.dp))
            Column {
                Text("Remove / Delete Sync Data", color = AccentRed, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("Delete local key or wipe Google Drive vault", color = TextSecond, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ReconnectScreen(email: String, onReconnect: () -> Unit, onBack: () -> Unit) {
    ScreenShell(onBack = onBack) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmber,
            modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(16.dp))
        Text("Reconnect Required", color = TextPrimary, fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
        Text("Your Google Drive authorization has expired. Re-authenticate to resume background sync.",
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
        Text("Remove Sync Data", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("Choose what to remove. This action cannot be undone.", color = TextSecond, fontSize = 14.sp)
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
                    "Deletes the key from this device. Your Drive vault files stay intact — you can restore access anytime with your passphrase.",
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
                    "Removes the local key AND deletes all encrypted batch files from Google Drive.",
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
