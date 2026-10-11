package com.health.friday

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.friday.ui.theme.FRIDAYTheme
import kotlinx.coroutines.delay

private val StartupCyan = Color(0xFF00E5FF)

class StartupActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {
            FRIDAYTheme(
                darkTheme = true,
                dynamicColor = false
            ) {
                StartupContent(
                    onFinished = {
                        openMainActivity()
                    }
                )
            }
        }
    }

    private fun openMainActivity() {

        startActivity(
            Intent(
                this,
                MainActivity::class.java
            )
        )

        finish()
    }
}

@Composable
private fun StartupContent(
    onFinished: () -> Unit
) {

    var visible by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(Unit) {

        delay(650L)

        visible = false

        delay(180L)

        onFinished()
    }

    StartupScreen(
        visible = visible
    )
}

@Composable
private fun StartupScreen(
    visible: Boolean
) {

    val fade by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "startup_fade"
    )

    val infiniteTransition =
        rememberInfiniteTransition(
            label = "startup_orb"
        )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 900,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .alpha(fade),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Canvas(
                modifier = Modifier.size(112.dp)
            ) {

                val middle =
                    Offset(size.width / 2f, size.height / 2f)

                val radius =
                    size.minDimension / 2f

                // soft glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            StartupCyan.copy(alpha = 0.35f),
                            Color.Transparent
                        ),
                        center = middle,
                        radius = radius
                    ),
                    radius = radius,
                    center = middle
                )

                // breathing ring
                drawCircle(
                    color = StartupCyan,
                    radius = radius * 0.62f * pulse,
                    center = middle,
                    style = Stroke(width = 3.dp.toPx())
                )

                // core
                drawCircle(
                    color = StartupCyan.copy(alpha = 0.9f),
                    radius = radius * 0.22f * pulse,
                    center = middle
                )

                // accent arc
                drawArc(
                    color = StartupCyan.copy(alpha = 0.55f),
                    startAngle = -30f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(
                        middle.x - radius * 0.82f,
                        middle.y - radius * 0.82f
                    ),
                    size = Size(radius * 1.64f, radius * 1.64f),
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            Spacer(
                modifier = Modifier.height(22.dp)
            )

            Text(
                text = "FRIDAY",
                color = Color(0xFFF2F6FA),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp
            )

            Spacer(
                modifier = Modifier.height(22.dp)
            )

            LinearProgressIndicator(
                modifier = Modifier
                    .width(92.dp)
                    .height(2.dp),
                color = StartupCyan,
                trackColor = Color(0xFF1A1A1A)
            )
        }
    }
}