package com.robomanipal.imusensor.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robomanipal.imusensor.sensor.SensorReading
import com.robomanipal.imusensor.ui.components.*
import com.robomanipal.imusensor.ui.theme.*
import com.robomanipal.imusensor.viewmodel.SensorViewModel

@Composable
fun DashboardScreen(
    vm: SensorViewModel,
    onSensorTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accel   by vm.latestAccel.collectAsStateWithLifecycle()
    val gyro    by vm.latestGyro.collectAsStateWithLifecycle()
    val mag     by vm.latestMag.collectAsStateWithLifecycle()
    val orient  by vm.orientation.collectAsStateWithLifecycle()
    val rate    by vm.sampleRateHz.collectAsStateWithLifecycle()

    val accelBuf by vm.accelBuffer.collectAsStateWithLifecycle()
    val gyroBuf  by vm.gyroBuffer.collectAsStateWithLifecycle()
    val magBuf   by vm.magBuffer.collectAsStateWithLifecycle()

    val view = LocalView.current

    // ── Staggered entrance ─────────────────────────────────────────
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60);  step = 1   // header
        kotlinx.coroutines.delay(100); step = 2   // YPR banner
        kotlinx.coroutines.delay(90);  step = 3   // card 0
        kotlinx.coroutines.delay(80);  step = 4   // card 1
        kotlinx.coroutines.delay(70);  step = 5   // card 2
        kotlinx.coroutines.delay(70);  step = 6   // orientation card
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header ─────────────────────────────────────────────────────
        val headAlpha by animateFloatAsState(
            if (step >= 1) 1f else 0f, tween(400), label = "hA"
        )
        val headOff by animateFloatAsState(
            if (step >= 1) 0f else 30f,
            spring(dampingRatio = 0.7f, stiffness = 200f), label = "hO"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = headAlpha; translationY = headOff },
        ) {
            Text(
                text = "IMU Sensor",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            StatusIndicator(
                color = StatusActive,
                label = "%.0f Hz".format(rate),
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── YPR Banner ─────────────────────────────────────────────────
        val bannerAlpha by animateFloatAsState(
            if (step >= 2) 1f else 0f, tween(400), label = "bA"
        )
        val bannerOff by animateFloatAsState(
            if (step >= 2) 0f else 40f,
            spring(dampingRatio = 0.65f, stiffness = 180f), label = "bO"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = bannerAlpha; translationY = bannerOff },
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = OrientColor,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    YPRValueChip("Y", orient.yaw, AccelColor)
                    YPRValueChip("P", orient.pitch, GyroColor)
                    YPRValueChip("R", orient.roll, MagColor)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Sensor cards (slide up from below) ─────────────────────────
        val cards = listOf(
            CardData("Accelerometer", "accel", AccelColor, accel, accelBuf, "m/s²"),
            CardData("Gyroscope", "gyro", GyroColor, gyro, gyroBuf, "rad/s"),
            CardData("Magnetometer", "mag", MagColor, mag, magBuf, "μT"),
        )

        cards.forEachIndexed { i, data ->
            val cardStep = i + 3
            val cAlpha by animateFloatAsState(
                if (step >= cardStep) 1f else 0f, tween(450), label = "cA$i"
            )
            val cOff by animateFloatAsState(
                if (step >= cardStep) 0f else 50f,
                spring(dampingRatio = 0.6f, stiffness = 160f), label = "cO$i"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = cAlpha; translationY = cOff },
            ) {
                SensorCardItem(
                    data = data,
                    onClick = {
                        HapticUtils.tick(view)
                        onSensorTap(data.route)
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── Orientation card ───────────────────────────────────────────
        val oAlpha by animateFloatAsState(
            if (step >= 6) 1f else 0f, tween(450), label = "oA"
        )
        val oOff by animateFloatAsState(
            if (step >= 6) 0f else 50f,
            spring(dampingRatio = 0.6f, stiffness = 160f), label = "oO"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = oAlpha; translationY = oOff },
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = OrientColor,
                onClick = {
                    HapticUtils.tick(view)
                    onSensorTap("orientation")
                },
            ) {
                Text(
                    text = "Orientation",
                    style = MaterialTheme.typography.titleMedium,
                    color = OrientColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                OrientationCube(
                    yawDeg   = orient.yaw,
                    pitchDeg = orient.pitch,
                    rollDeg  = orient.roll,
                    edgeColor = OrientColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    YPRValueChip("Yaw", orient.yaw, AccelColor)
                    YPRValueChip("Pitch", orient.pitch, GyroColor)
                    YPRValueChip("Roll", orient.roll, MagColor)
                }
            }
        }

        Spacer(Modifier.height(100.dp)) // space for bottom nav
    }
}

// ── Private helpers ────────────────────────────────────────────────────────

@Composable
private fun YPRValueChip(label: String, value: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 160f),
        label = "yprChip",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = 0.7f),
        )
        Text(
            text = "%+.1f°".format(animated),
            fontFamily = SensorValueFont,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.95f),
        )
    }
}

private data class CardData(
    val title: String,
    val route: String,
    val color: Color,
    val latest: SensorReading,
    val buffer: List<SensorReading>,
    val unit: String,
)

@Composable
private fun SensorCardItem(data: CardData, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        accentColor = data.color,
        onClick = onClick,
    ) {
        Text(
            text = data.title,
            style = MaterialTheme.typography.titleMedium,
            color = data.color,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))

        // Mini chart
        SensorChart(
            dataX = data.buffer.map { it.x },
            dataY = data.buffer.map { it.y },
            dataZ = data.buffer.map { it.z },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            maxPoints = 100,
        )

        Spacer(Modifier.height(8.dp))

        // X / Y / Z values
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            AxisValue("X", data.latest.x, AxisXColor)
            AxisValue("Y", data.latest.y, AxisYColor)
            AxisValue("Z", data.latest.z, AxisZColor)
        }
    }
}

@Composable
private fun AxisValue(axis: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$axis ",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = "%+.2f".format(value),
            fontFamily = SensorValueFont,
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}
