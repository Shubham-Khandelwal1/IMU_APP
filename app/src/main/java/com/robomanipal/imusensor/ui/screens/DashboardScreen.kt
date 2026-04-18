package com.robomanipal.imusensor.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    // Staggered entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
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
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500)) + expandVertically(tween(500)),
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

        // ── 2×2 Sensor cards ───────────────────────────────────────────
        val cards = listOf(
            CardData("Accelerometer", "accel", AccelColor, accel, accelBuf, "m/s²"),
            CardData("Gyroscope", "gyro", GyroColor, gyro, gyroBuf, "rad/s"),
            CardData("Magnetometer", "mag", MagColor, mag, magBuf, "μT"),
        )

        cards.forEachIndexed { i, data ->
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayMillis = 100 + i * 80)) +
                        scaleIn(initialScale = 0.92f, animationSpec = tween(500, delayMillis = 100 + i * 80)),
            ) {
                SensorCardItem(data = data, onClick = { onSensorTap(data.route) })
            }
            Spacer(Modifier.height(12.dp))
        }

        // Orientation card
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500, delayMillis = 380)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(500, delayMillis = 380)),
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = OrientColor,
                onClick = { onSensorTap("orientation") },
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = 0.7f),
        )
        Text(
            text = "%+.1f°".format(value),
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
