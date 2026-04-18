package com.robomanipal.imusensor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 * Shows a large chart, live XYZ values, and basic statistics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorDetailScreen(
    vm: SensorViewModel,
    sensorType: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolve which data to show
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .statusBarsPadding(),
    ) {
        // ── Top bar ────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
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
            StatusIndicator(color = StatusActive, label = "%.0f Hz".format(rate))
            Spacer(Modifier.width(12.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ── Large chart ────────────────────────────────────────────
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = color,
            ) {
                SensorChart(
                    dataX = buffer.map { it.x },
                    dataY = buffer.map { it.y },
                    dataZ = buffer.map { it.z },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Live values ────────────────────────────────────────────
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = color,
            ) {
                SensorValueRow(label = "X", value = latest.x, unit = unit, color = AxisXColor)
                SensorValueRow(label = "Y", value = latest.y, unit = unit, color = AxisYColor)
                SensorValueRow(label = "Z", value = latest.z, unit = unit, color = AxisZColor)
            }

            Spacer(Modifier.height(16.dp))

            // ── Statistics ─────────────────────────────────────────────
            if (buffer.isNotEmpty()) {
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

                    Spacer(Modifier.height(8.dp))
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
