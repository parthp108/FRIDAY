
package com.health.friday.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.health.friday.device.HealthConnectRepository
import com.health.friday.device.HealthSummary
import com.health.friday.ui.components.InfoCard
import com.health.friday.ui.components.ScreenHeader
import com.health.friday.ui.components.StatCard
import com.health.friday.ui.theme.FridayBackground
import com.health.friday.ui.theme.FridayBlue
import com.health.friday.ui.theme.FridayCyan
import com.health.friday.ui.theme.FridayGreen
import com.health.friday.ui.theme.FridayOrange
import com.health.friday.ui.theme.FridayRed
import kotlinx.coroutines.launch

@Composable
fun HealthScreen(
    modifier: Modifier = Modifier
) {
    val context =
        androidx.compose.ui.platform.LocalContext.current

    val repository =
        remember {
            HealthConnectRepository(context)
        }

    val coroutineScope =
        rememberCoroutineScope()

    /*
     * null  = still checking Health Connect
     * true  = permissions are already granted
     * false = permissions are actually missing
     *
     * Starting with null prevents the "Connect Health" screen
     * from flashing every time this screen is opened.
     */
    var hasAccess by remember {
        mutableStateOf<Boolean?>(null)
    }

    var healthSummary by remember {
        mutableStateOf<HealthSummary?>(null)
    }

    /*
     * null  = still checking availability
     * true  = Health Connect available
     * false = Health Connect unavailable
     */
    var isAvailable by remember {
        mutableStateOf<Boolean?>(null)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                PermissionController
                    .createRequestPermissionResultContract()
        ) {
            coroutineScope.launch {

                val access =
                    repository.hasAccess()

                hasAccess =
                    access

                if (access) {
                    healthSummary =
                        repository.getTodayHealth()
                }
            }
        }

    LaunchedEffect(Unit) {

        val available =
            repository.isAvailable()

        isAvailable =
            available

        if (!available) {
            hasAccess = false
            return@LaunchedEffect
        }

        val access =
            repository.hasAccess()

        hasAccess =
            access

        if (access) {

            healthSummary =
                repository.getTodayHealth()

        } else {

            permissionLauncher.launch(
                repository.requiredPermissions
            )
        }
    }

    /*
     * ---------------------------------------------------------
     * INITIAL CHECK
     * ---------------------------------------------------------
     *
     * Do not show the permission screen while we are still
     * checking the existing Health Connect permission.
     *
     * This is the part that removes the visible blink.
     */
    if (
        isAvailable == null ||
        hasAccess == null
    ) {
        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(
                        FridayBackground
                    ),

            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    top = 18.dp,
                    end = 18.dp,
                    bottom = 96.dp
                )
        ) {
            item {
                ScreenHeader(
                    title = "Health",
                    subtitle = "Body signals"
                )
            }
        }

        return
    }

    /*
     * ---------------------------------------------------------
     * HEALTH CONNECT UNAVAILABLE
     * ---------------------------------------------------------
     */
    if (!isAvailable!!) {

        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(
                        FridayBackground
                    ),

            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    top = 18.dp,
                    end = 18.dp,
                    bottom = 96.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {

                ScreenHeader(
                    title = "Health",
                    subtitle = "Body signals"
                )
            }

            item {

                InfoCard(
                    title = "Health Connect unavailable",

                    body =
                        "Health Connect is not currently available on this device.",

                    accent =
                        FridayBlue
                )
            }
        }

        return
    }

    /*
     * ---------------------------------------------------------
     * PERMISSION NOT GRANTED
     * ---------------------------------------------------------
     *
     * This branch is reached only after we have actually checked
     * Health Connect and confirmed that permission is missing.
     */
    if (!hasAccess!!) {

        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(
                        FridayBackground
                    ),

            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    top = 18.dp,
                    end = 18.dp,
                    bottom = 96.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            item {

                ScreenHeader(
                    title = "Health",
                    subtitle = "Connect your health data"
                )
            }

            item {

                InfoCard(
                    title = "Health Connect",

                    body =
                        "FRIDAY needs permission to read your steps, heart rate and sleep data.",

                    accent =
                        FridayBlue
                )
            }

            item {

                Button(
                    onClick = {

                        permissionLauncher.launch(
                            repository.requiredPermissions
                        )
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "Connect Health"
                    )
                }
            }
        }

        return
    }

    val data =
        healthSummary

    /*
     * ---------------------------------------------------------
     * NORMAL HEALTH SCREEN
     * ---------------------------------------------------------
     *
     * Existing real data:
     * - Steps
     * - Heart rate
     * - Sleep
     *
     * Future wearable data remains unavailable until an actual
     * provider supplies it.
     */

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    FridayBackground
                ),

        contentPadding =
            PaddingValues(
                start = 18.dp,
                top = 18.dp,
                end = 18.dp,
                bottom = 96.dp
            ),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            ScreenHeader(
                title = "Health",
                subtitle = "Body signals"
            )
        }

        // ---------------------------------------------------------
        // ACTIVITY
        // ---------------------------------------------------------

        item {

            Text(
                "ACTIVITY"
            )
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "STEPS",

                    value =
                        data?.steps?.toString()
                            ?: "—",

                    detail =
                        "Today",

                    accent =
                        FridayGreen,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "DISTANCE",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayCyan,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "ACTIVE CALORIES",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayOrange,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "TOTAL CALORIES",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayOrange,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "FLOORS",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayGreen,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "ACTIVE TIME",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayBlue,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------------
        // HEART
        // ---------------------------------------------------------

        item {

            Text(
                "HEART"
            )
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "HEART RATE",

                    value =
                        data?.heartRate?.let {
                            "$it"
                        } ?: "—",

                    detail =
                        if (data?.heartRate != null)
                            "Latest bpm"
                        else
                            "No data",

                    accent =
                        FridayRed,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "RESTING HR",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayRed,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "HRV",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayRed,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "EXERCISE HR",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayRed,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "HR ZONES",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayRed,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "ARRHYTHMIA",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayRed,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------------
        // SLEEP
        // ---------------------------------------------------------

        item {

            Text(
                "SLEEP"
            )
        }

        item {

            StatCard(
                label = "TOTAL SLEEP",

                value =
                    data?.let {
                        formatSleep(
                            it.sleepMinutes
                        )
                    } ?: "—",

                detail =
                    "Today",

                accent =
                    FridayBlue,

                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "SLEEP STAGES",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayBlue,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "NAPS",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayBlue,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "SLEEP HR",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayBlue,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "SLEEP HRV",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayBlue,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------------
        // BLOOD & RESPIRATION
        // ---------------------------------------------------------

        item {

            Text(
                "BLOOD & RESPIRATION"
            )
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "SpO₂",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayCyan,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "BREATHING RATE",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayCyan,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------------
        // WORKOUT
        // ---------------------------------------------------------

        item {

            Text(
                "WORKOUT"
            )
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "WORKOUT",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayGreen,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "DURATION",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayGreen,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "WORKOUT CALORIES",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayOrange,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "WORKOUT DISTANCE",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayOrange,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "AVG HR",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayRed,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "PACE / SPEED",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayGreen,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "CADENCE",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayGreen,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "VO₂ MAX",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayBlue,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------------
        // WELLNESS
        // ---------------------------------------------------------

        item {

            Text(
                "WELLNESS"
            )
        }

        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                StatCard(
                    label = "STRESS",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayCyan,

                    modifier =
                        Modifier.weight(1f)
                )

                StatCard(
                    label = "BODY DATA",

                    value =
                        "—",

                    detail =
                        "Not available",

                    accent =
                        FridayBlue,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        // ---------------------------------------------------------
        // CONNECTION
        // ---------------------------------------------------------

        item {

            InfoCard(
                title =
                    "Health Connect",

                body =
                    "FRIDAY is connected to Health Connect and reading your available health data.",

                accent =
                    FridayGreen
            )
        }
    }
}

private fun formatSleep(
    minutes: Long
): String {

    if (minutes <= 0L) {
        return "0h 0m"
    }

    val hours =
        minutes / 60

    val remainingMinutes =
        minutes % 60

    return "${hours}h ${remainingMinutes}m"
}

