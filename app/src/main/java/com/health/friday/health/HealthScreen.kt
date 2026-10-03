package com.health.friday.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.health.friday.ui.theme.FridayGreen
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

    var hasAccess by remember {
        mutableStateOf(false)
    }

    var healthSummary by remember {
        mutableStateOf<HealthSummary?>(null)
    }

    var isAvailable by remember {
        mutableStateOf(true)
    }

    /*
     * Health Connect permission launcher.
     *
     * This opens the actual Health Connect permission screen.
     */
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                PermissionController
                    .createRequestPermissionResultContract()
        ) {
            coroutineScope.launch {
                hasAccess = repository.hasAccess()

                if (hasAccess) {
                    healthSummary =
                        repository.getTodayHealth()
                }
            }
        }

    /*
     * Check Health Connect every time
     * the Health screen opens.
     */
    LaunchedEffect(Unit) {

        isAvailable =
            repository.isAvailable()

        if (!isAvailable) {
            return@LaunchedEffect
        }

        hasAccess =
            repository.hasAccess()

        if (!hasAccess) {

            permissionLauncher.launch(
                repository.requiredPermissions
            )

        } else {

            healthSummary =
                repository.getTodayHealth()
        }
    }

    /*
     * Health Connect unavailable.
     */
    if (!isAvailable) {

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(FridayBackground),

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
                    body = "Health Connect is not currently available on this device.",
                    accent = FridayBlue
                )
            }
        }

        return
    }

    /*
     * Permission not granted.
     */
    if (!hasAccess) {

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(FridayBackground),

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
                    body = "FRIDAY needs permission to read your steps, heart rate and sleep data.",
                    accent = FridayBlue
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

                    Text("Connect Health")
                }
            }
        }

        return
    }

    /*
     * Normal Health screen.
     */
    val data =
        healthSummary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(FridayBackground),

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

                    detail = "Today",

                    accent = FridayGreen,

                    modifier =
                        Modifier.weight(1f)
                )

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

                    accent = FridayRed,

                    modifier =
                        Modifier.weight(1f)
                )
            }
        }

        item {

            StatCard(
                label = "SLEEP",

                value =
                    data?.let {
                        formatSleep(it.sleepMinutes)
                    } ?: "—",

                detail = "Today",

                accent = FridayBlue,

                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        item {

            InfoCard(
                title = "Health Connect",
                body = "FRIDAY is connected to Health Connect and reading your available health data.",
                accent = FridayGreen
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