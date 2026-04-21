package com.robomanipal.imusensor.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robomanipal.imusensor.sensor.SensorReading
import com.robomanipal.imusensor.ui.components.*
import com.robomanipal.imusensor.ui.theme.*
import com.robomanipal.imusensor.viewmodel.SensorViewModel
import kotlin.math.sqrt

/**
 * Detailed view for a single sensor type (accel / gyro / mag).
 * Shows a large chart with value pills, live XYZ values, and statistics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailScreen(
    vm: SensorViewModel,
    sensorType: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title: String
    val color: Color
    val unit: String
    val latest: SensorReading
    val buffer: List<SensorReading>
    val rate by vm.sampleRateHz.collectAsStateWithLifecycle()

    when (sensorType) {
        "accel" -> {
            title = "Accelerometer"
            color = AccelColor
            unit  = "m/s²"
            latest = vm.latestAccel.collectAsStateWithLifecycle().value
            buffer = vm.accelBuffer.collectAsStateWithLifecycle().value
        }
        "gyro" -> {
            title = "Gyroscope"
            color = GyroColor
            unit  = "rad/s"
            latest = vm.latestGyro.collectAsStateWithLifecycle().value
            buffer = vm.gyroBuffer.collectAsStateWithLifecycle().value
        }
        else -> {
            title = "Magnetometer"
            color = MagColor
            unit  = "μT"
            latest = vm.latestMag.collectAsStateWithLifecycle().value
            buffer = vm.magBuffer.collectAsStateWithLifecycle().value
        }
    }

    // ── Entrance animation ─────────────────────────────────────────
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60);  step = 1
        kotlinx.coroutines.delay(100); step = 2
        kotlinx.coroutines.delay(90);  step = 3
    }

    val sa1 by animateFloatAsState(if (step >= 1) 1f else 0f, tween(400), label = "da1")
    val so1 by animateFloatAsState(if (step >= 1) 0f else 30f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "do1")
    val sa2 by animateFloatAsState(if (step >= 2) 1f else 0f, tween(450), label = "da2")
    val so2 by animateFloatAsState(if (step >= 2) 0f else 40f, spring(dampingRatio = 0.6f, stiffness = 160f), label = "do2")
    val sa3 by animateFloatAsState(if (step >= 3) 1f else 0f, tween(450), label = "da3")
    val so3 by animateFloatAsState(if (step >= 3) 0f else 40f, spring(dampingRatio = 0.6f, stiffness = 160f), label = "do3")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A16),
                        DarkBackground,
                        DarkBackground,
                    )
                )
            )
            .statusBarsPadding(),
    ) {
        // ── Top bar ────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .graphicsLayer { alpha = sa1; translationY = so1 },
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(StatusActive.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "%.0f Hz".format(rate),
                    fontFamily = SensorValueFont,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusActive,
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ── Large chart with value pills ──────────────────────────
            Box(modifier = Modifier.graphicsLayer { alpha = sa1; translationY = so1 }) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = color,
                    accentGlow = true,
                ) {
                    SensorChart(
                        dataX = buffer.map { it.x },
                        dataY = buffer.map { it.y },
                        dataZ = buffer.map { it.z },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        showValuePills = true,
                        showAxisLabels = true,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Live values ────────────────────────────────────────────
            Box(modifier = Modifier.graphicsLayer { alpha = sa2; translationY = so2 }) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = color,
                ) {
                    SensorValueRow(label = "X", value = latest.x, unit = unit, color = AxisXColor)
                    SensorValueRow(label = "Y", value = latest.y, unit = unit, color = AxisYColor)
                    SensorValueRow(label = "Z", value = latest.z, unit = unit, color = AxisZColor)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Statistics ─────────────────────────────────────────────
            if (buffer.isNotEmpty()) {
                Box(modifier = Modifier.graphicsLayer { alpha = sa3; translationY = so3 }) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = color,
                    ) {
                        Text(
                            text = "Statistics",
                            style = MaterialTheme.typography.titleMedium,
                            color = color,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))

                        val xs = buffer.map { it.x }
                        val ys = buffer.map { it.y }
                        val zs = buffer.map { it.z }
                        val mag = sqrt(
                            latest.x * latest.x + latest.y * latest.y + latest.z * latest.z
                        )

                        StatRow("Min",  "%.3f".format(xs.min()), "%.3f".format(ys.min()), "%.3f".format(zs.min()))
                        StatRow("Max",  "%.3f".format(xs.max()), "%.3f".format(ys.max()), "%.3f".format(zs.max()))
                        StatRow("Mean", "%.3f".format(xs.average()), "%.3f".format(ys.average()), "%.3f".format(zs.average()))

                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Magnitude",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "%.4f $unit".format(mag),
                                fontFamily = SensorValueFont,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = color,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun StatRow(label: String, x: String, y: String, z: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier.width(44.dp),
        )
        Text(text = x, fontFamily = SensorValueFont, fontSize = 13.sp, color = AxisXColor, modifier = Modifier.weight(1f))
        Text(text = y, fontFamily = SensorValueFont, fontSize = 13.sp, color = AxisYColor, modifier = Modifier.weight(1f))
        Text(text = z, fontFamily = SensorValueFont, fontSize = 13.sp, color = AxisZColor, modifier = Modifier.weight(1f))
    }
}
