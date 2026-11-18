package com.health.friday.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.health.friday.device.BodySummary
import com.health.friday.device.HealthConnectRepository
import com.health.friday.device.HealthSummary
import com.health.friday.device.SleepStageSummary
import com.health.friday.ui.components.InfoCard
import com.health.friday.ui.components.ScreenHeader
import com.health.friday.ui.components.StatCard
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayBlue
import com.health.friday.ui.theme.FridayCard
import com.health.friday.ui.theme.FridayCardLight
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayGreen
import com.health.friday.ui.theme.FridayMuted
import com.health.friday.ui.theme.FridayOrange
import com.health.friday.ui.theme.FridayRed
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(
    repository: HealthConnectRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val healthRepo = remember(repository) {
        repository ?: HealthConnectRepository(context)
    }

    val coroutineScope = rememberCoroutineScope()

    var hasAccess by remember {
        mutableStateOf<Boolean?>(null)
    }

    var hasFullAccess by remember {
        mutableStateOf(false)
    }

    var supportsHistoryRead by remember {
        mutableStateOf(false)
    }

    var hasHistoryReadAccess by remember {
        mutableStateOf(false)
    }

    var healthSummary by remember {
        mutableStateOf<HealthSummary?>(null)
    }

    var isAvailable by remember {
        mutableStateOf<Boolean?>(null)
    }

    var isRefreshing by remember {
        mutableStateOf(false)
    }

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    val tabs = listOf("Overview", "Activity", "Heart", "Sleep", "Vitals", "Body")

    val hasShealth = remember(context) {
        healthRepo.isSamsungHealthInstalled(context)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract()
        ) {
            coroutineScope.launch {
                val access = healthRepo.hasAccess()
                hasAccess = access
                hasFullAccess = healthRepo.hasFullAccess()
                if (access) {
                    healthSummary = healthRepo.getTodayHealth()
                }
            }
        }

    val historyPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract()
        ) { grantedPermissions ->
            coroutineScope.launch {
                hasHistoryReadAccess =
                    healthRepo.historyReadPermission in grantedPermissions

                if (hasHistoryReadAccess) {
                    healthSummary = healthRepo.getTodayHealth()
                }
            }
        }

    LaunchedEffect(healthRepo) {
        val available = healthRepo.isAvailable()
        isAvailable = available

        if (!available) {
            hasAccess = false
            hasFullAccess = false
            return@LaunchedEffect
        }

        supportsHistoryRead = healthRepo.supportsHistoryRead()
        hasHistoryReadAccess = healthRepo.hasHistoryReadAccess()

        val granted = healthRepo.getGrantedPermissions()
        hasAccess = granted.isNotEmpty()
        hasFullAccess = granted.containsAll(healthRepo.requiredPermissions)

        if (hasAccess == true) {
            healthSummary = healthRepo.getTodayHealth()
        }

        if (!hasFullAccess) {
            permissionLauncher.launch(healthRepo.requiredPermissions)
        }
    }

    if (isAvailable == null || hasAccess == null) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(FridayBackground),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp)
        ) {
            item {
                ScreenHeader(title = "Health", subtitle = "Live Body Telemetry")
            }
        }
        return
    }

    if (!isAvailable!!) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(FridayBackground),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(title = "Health", subtitle = "Live Body Telemetry")
            }
            item {
                InfoCard(
                    title = "Health Connect Unavailable",
                    body = "Health Connect framework is not available on this device.",
                    accent = FridayBlue
                )
            }
        }
        return
    }

    if (!hasAccess!!) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(FridayBackground),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(title = "Health", subtitle = "Connect Health Data")
            }
            item {
                InfoCard(
                    title = "Health Connect Permissions",
                    body = "FRIDAY requires permission to sync live steps, heart rate, sleep, workouts, body metrics, and vitals.",
                    accent = FridayBlue
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { permissionLauncher.launch(healthRepo.requiredPermissions) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Connect Health")
                    }

                    OutlinedButton(
                        onClick = { healthRepo.openHealthConnectSettings(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Open App")
                    }
                }
            }
        }
        return
    }

    val data = healthSummary

    val onRefresh: () -> Unit = {
        coroutineScope.launch {
            isRefreshing = true
            healthSummary = healthRepo.getTodayHealth()
            hasFullAccess = healthRepo.hasFullAccess()
            isRefreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(FridayBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                ScreenHeader(title = "Health", subtitle = "Live Body Telemetry")
            }

            if (supportsHistoryRead && !hasHistoryReadAccess) {
                item(key = "history_permission") {
                    InfoCard(
                        title = "Enable Full Health History",
                        body = "Allow FRIDAY to read health records older than the default 30-day access window. This permission is optional and controlled by you.",
                        accent = FridayOrange,
                        modifier = Modifier.clickable {
                            historyPermissionLauncher.launch(
                                setOf(healthRepo.historyReadPermission)
                            )
                        }
                    )
                }
            }

            if (!hasFullAccess) {
                item(key = "permission_banner") {
                    InfoCard(
                        title = "Grant Full Permissions",
                        body = "Tap to grant FRIDAY access to all 17+ health metrics.",
                        accent = FridayOrange,
                        modifier = Modifier.clickable {
                            permissionLauncher.launch(healthRepo.requiredPermissions)
                        }
                    )
                }
            }

            // ---------------------------------------------------------
            // CATEGORY TAB ROW
            // ---------------------------------------------------------
            item(key = "tab_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(tabs) { index, title ->
                        val isSelected = index == selectedTab
                        val bg = if (isSelected) FridayCyan.copy(alpha = 0.15f) else FridayCard
                        val border = if (isSelected) FridayCyan else FridayCardLight
                        val textColor = if (isSelected) FridayCyan else FridayMuted

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(20.dp))
                                .clickable { selectedTab = index }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = title.uppercase(),
                                color = textColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------
            // TAB SPECIFIC CONTENT
            // ---------------------------------------------------------
            when (selectedTab) {
                0 -> {
                    // OVERVIEW
                    item(key = "ov_activity") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "STEPS",
                                value = data?.steps?.toString() ?: "—",
                                detail = "Today",
                                accent = FridayGreen,
                                progress = (data?.steps?.toFloat() ?: 0f) / 10000f,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "DISTANCE",
                                value = data?.distanceMeters?.let { formatDistance(it) } ?: "—",
                                detail = if (data?.distanceMeters != null) (if (data.isDistanceDerived) "Est. from steps" else "Today") else "No data",
                                accent = FridayCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "ov_heart") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "HEART RATE",
                                value = data?.heartRate?.let { "$it bpm" } ?: "—",
                                detail = if (data?.heartRate != null) "Latest bpm" else "No data",
                                accent = FridayRed,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "RESTING HR",
                                value = data?.restingHeartRate?.let { "$it bpm" } ?: "—",
                                detail = if (data?.restingHeartRate != null) "Resting avg" else "No data",
                                accent = FridayRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "ov_sleep") {
                        StatCard(
                            label = "TOTAL SLEEP",
                            value = data?.let { formatSleep(it.sleepMinutes) } ?: "—",
                            detail = data?.sleepStages?.let { formatSleepStages(it) } ?: "Today",
                            accent = FridayBlue,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item(key = "ov_calories") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "ACTIVE CALORIES",
                                value = data?.activeCaloriesKcal?.let { "$it kcal" } ?: "—",
                                detail = if (data?.activeCaloriesKcal != null) (if (data.isCaloriesDerived) "Est. from steps" else "Burned today") else "No data",
                                accent = FridayOrange,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "TOTAL CALORIES",
                                value = data?.totalCaloriesKcal?.let { "$it kcal" } ?: "—",
                                detail = if (data?.totalCaloriesKcal != null) "Active + Basal" else "No data",
                                accent = FridayOrange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                1 -> {
                    // ACTIVITY
                    item(key = "act_steps") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "STEPS",
                                value = data?.steps?.toString() ?: "—",
                                detail = "Goal: 10,000",
                                accent = FridayGreen,
                                progress = (data?.steps?.toFloat() ?: 0f) / 10000f,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "DISTANCE",
                                value = data?.distanceMeters?.let { formatDistance(it) } ?: "—",
                                detail = if (data?.distanceMeters != null) (if (data.isDistanceDerived) "Est. from steps" else "Today") else "No data",
                                accent = FridayCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "act_calories") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "ACTIVE CALORIES",
                                value = data?.activeCaloriesKcal?.let { "$it kcal" } ?: "—",
                                detail = if (data?.activeCaloriesKcal != null) (if (data.isCaloriesDerived) "Est. from steps" else "Burned today") else "No data",
                                accent = FridayOrange,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "TOTAL CALORIES",
                                value = data?.totalCaloriesKcal?.let { "$it kcal" } ?: "—",
                                detail = if (data?.totalCaloriesKcal != null) "Active + Basal" else "No data",
                                accent = FridayOrange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "act_time") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "ACTIVE TIME",
                                value = data?.activeTimeMinutes?.takeIf { it > 0 }?.let { formatMinutes(it) } ?: "—",
                                detail = if (data?.activeTimeMinutes != 0L) "Exercise duration" else "No data",
                                accent = FridayBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "FLOORS",
                                value = data?.floorsClimbed?.let { "${it.toInt()} floors" } ?: "—",
                                detail = if (data?.floorsClimbed != null) "Climbed today" else "No data",
                                accent = FridayGreen,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (data?.workout != null) {
                        item(key = "act_workout") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatCard(
                                    label = "WORKOUT",
                                    value = data.workout.type ?: "Workout",
                                    detail = data.workout.title ?: "Latest session",
                                    accent = FridayGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    label = "WORKOUT SPEED",
                                    value = data.workout.avgSpeedKmh?.let { String.format(Locale.getDefault(), "%.1f km/h", it) } ?: "—",
                                    detail = "Avg speed",
                                    accent = FridayGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // HEART
                    item(key = "hrt_main") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "HEART RATE",
                                value = data?.heartRate?.let { "$it bpm" } ?: "—",
                                detail = if (data?.heartRate != null) "Latest bpm" else "No data",
                                accent = FridayRed,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "RESTING HR",
                                value = data?.restingHeartRate?.let { "$it bpm" } ?: "—",
                                detail = if (data?.restingHeartRate != null) "Resting avg" else "No data",
                                accent = FridayRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "hrt_zones") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "HR ZONES",
                                value = data?.heartRateZone ?: "—",
                                detail = if (data?.heartRateZone != null) "Current zone" else "No data",
                                accent = FridayRed,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "EXERCISE HR",
                                value = data?.exerciseHeartRate?.let { "$it bpm" } ?: "—",
                                detail = if (data?.exerciseHeartRate != null) "Avg in workout" else "No data",
                                accent = FridayRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "hrt_hrv") {
                        StatCard(
                            label = "HEART RATE VARIABILITY (HRV)",
                            value = data?.hrvRmssdMs?.let { "${it.toInt()} ms" } ?: "—",
                            detail = if (data?.hrvRmssdMs != null) "RMSSD recovery metric" else "No HRV data",
                            accent = FridayRed,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                3 -> {
                    // SLEEP
                    item(key = "slp_total") {
                        StatCard(
                            label = "TOTAL SLEEP",
                            value = data?.let { formatSleep(it.sleepMinutes) } ?: "—",
                            detail = "Today",
                            accent = FridayBlue,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item(key = "slp_stages") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "SLEEP STAGES",
                                value = data?.sleepStages?.let { formatSleepStages(it) } ?: "—",
                                detail = if (data?.sleepStages != null) "Deep / REM / Light" else "No stage data",
                                accent = FridayBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "NAPS",
                                value = data?.let { if (it.napCount > 0) "${it.napCount} (${formatSleep(it.napTotalMinutes)})" else "0 naps" } ?: "0 naps",
                                detail = "Daytime naps",
                                accent = FridayBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "slp_vitals") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "SLEEP HR",
                                value = data?.sleepHeartRate?.let { "$it bpm" } ?: "—",
                                detail = if (data?.sleepHeartRate != null) "Avg sleep HR" else "No data",
                                accent = FridayBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "SLEEP HRV",
                                value = data?.sleepHrvRmssdMs?.let { "${it.toInt()} ms" } ?: "—",
                                detail = if (data?.sleepHrvRmssdMs != null) "Sleep RMSSD" else "No data",
                                accent = FridayBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                4 -> {
                    // VITALS
                    item(key = "vtl_resp") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "SpO₂",
                                value = data?.oxygenSaturationPercentage?.let { "${it.toInt()}%" } ?: "—",
                                detail = if (data?.oxygenSaturationPercentage != null) "Blood oxygen" else "No data",
                                accent = FridayCyan,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "BREATHING RATE",
                                value = data?.respiratoryRateBpm?.let { "${it.toInt()} rpm" } ?: "—",
                                detail = if (data?.respiratoryRateBpm != null) "Breaths per min" else "No data",
                                accent = FridayCyan,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "vtl_bp_glucose") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "BLOOD PRESSURE",
                                value = data?.vitals?.systolicBp?.let { sys ->
                                    val dia = data.vitals.diastolicBp?.toInt() ?: 0
                                    "${sys.toInt()}/$dia"
                                } ?: "—",
                                detail = if (data?.vitals?.systolicBp != null) "mmHg" else "No data",
                                accent = FridayRed,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "BLOOD GLUCOSE",
                                value = data?.vitals?.bloodGlucoseMmol?.let {
                                    String.format(Locale.getDefault(), "%.1f mmol/L", it)
                                } ?: "—",
                                detail = if (data?.vitals?.bloodGlucoseMmol != null) "Glucose level" else "No data",
                                accent = FridayOrange,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                else -> {
                    // BODY
                    item(key = "bdy_comp") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatCard(
                                label = "BODY DATA",
                                value = data?.body?.let { formatBodyData(it) } ?: "—",
                                detail = if (data?.body != null) "Weight & Fat %" else "No body data",
                                accent = FridayBlue,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "VO₂ MAX",
                                value = data?.vo2Max?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
                                detail = if (data?.vo2Max != null) "mL/kg/min" else "No data",
                                accent = FridayBlue,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item(key = "bdy_wellness") {
                        StatCard(
                            label = "STRESS INDEX",
                            value = data?.stressScore ?: "—",
                            detail = if (data?.stressScore != null) "HRV stress level" else "No stress data",
                            accent = FridayCyan,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ---------------------------------------------------------
            // CONNECTION & DATA SOURCES
            // ---------------------------------------------------------
            item(key = "card_health_connect") {
                InfoCard(
                    title = "Health Connect",
                    body = "FRIDAY is connected to Health Connect. Tap to open Health Connect settings & manage permissions.",
                    accent = FridayGreen,
                    modifier = Modifier.clickable {
                        healthRepo.openHealthConnectSettings(context)
                    }
                )
            }

            item(key = "card_shealth") {
                InfoCard(
                    title = "Samsung Health Sync",
                    body = if (hasShealth) {
                        "Samsung Health syncs Galaxy Watch metrics into Health Connect. Tap to open Samsung Health & verify sync."
                    } else {
                        "Sync Galaxy Watch or Samsung Health metrics by enabling Health Connect in Samsung Health settings."
                    },
                    accent = FridayBlue,
                    modifier = Modifier.clickable {
                        if (hasShealth) {
                            healthRepo.openSamsungHealth(context)
                        } else {
                            healthRepo.openHealthConnectSettings(context)
                        }
                    }
                )
            }
        }
    }
}

private fun formatDistance(meters: Double): String {
    return if (meters >= 1000) {
        String.format(Locale.getDefault(), "%.2f km", meters / 1000.0)
    } else {
        "${meters.toInt()} m"
    }
}

private fun formatMinutes(minutes: Long): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}

private fun formatSleep(minutes: Long): String {
    if (minutes <= 0L) {
        return "0h 0m"
    }
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return "${hours}h ${remainingMinutes}m"
}

private fun formatSleepStages(stages: SleepStageSummary): String {
    val deep = if (stages.deepMinutes > 0) "${stages.deepMinutes}m deep" else null
    val rem = if (stages.remMinutes > 0) "${stages.remMinutes}m REM" else null
    val light = if (stages.lightMinutes > 0) "${stages.lightMinutes}m light" else null
    val parts = listOfNotNull(deep, rem, light)
    return if (parts.isNotEmpty()) parts.joinToString(" • ") else "Tracked"
}

private fun formatBodyData(body: BodySummary): String {
    val weightStr = body.weightKg?.let { String.format(Locale.getDefault(), "%.1f kg", it) }
    val fatStr = body.bodyFatPercentage?.let { String.format(Locale.getDefault(), "%.1f%% fat", it) }
    val heightStr = body.heightMeters?.let { String.format(Locale.getDefault(), "%.2f m", it) }
    val bmrStr = body.bmrKcal?.let { "$it kcal BMR" }
    return listOfNotNull(weightStr, fatStr, heightStr, bmrStr).joinToString(" • ").ifEmpty { "—" }
}
