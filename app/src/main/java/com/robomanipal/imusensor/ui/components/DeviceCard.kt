package com.robomanipal.imusensor.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robomanipal.imusensor.sensor.SensorRepository
import com.robomanipal.imusensor.ui.theme.*

/**
 * A single sensor row inside the DeviceCard.
 */
data class DeviceSensorInfo(
    val label: String,
    val color: Color,
    val available: Boolean,
    val meta: SensorRepository.SensorMeta?,
    val unit: String,
    val sensorType: String, // for navigation: "accel", "gyro", "mag", "orientation"
)

/**
 * Premium Device Identity Card — Tesla-style hero showing the phone
 * model, sensor hardware chips, live sample rate, battery temperature,
 * and uptime. Each sensor row is tappable to navigate to detail.
 */
@Composable
fun DeviceCard(
    sensors: List<DeviceSensorInfo>,
    sampleRateHz: Float,
    uptimeSeconds: Long,
    onSensorTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val view = LocalView.current

    // Device info
    val manufacturer = remember { Build.MANUFACTURER.replaceFirstChar { it.uppercase() } }
    val model = remember { Build.MODEL }
    val androidVersion = remember { "Android ${Build.VERSION.RELEASE}" }

    // Battery temperature (reads from sticky broadcast — no permission needed)
    val batteryTemp = remember { getBatteryTemp(ctx) }

    // Entrance animation
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        step = 1
    }
    val enterAlpha by animateFloatAsState(
        if (step >= 1) 1f else 0f, tween(500), label = "dcEnter"
    )

    // Animated rate value
    val animatedRate by animateFloatAsState(
        targetValue = sampleRateHz,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 100f),
        label = "dcRate",
    )

    // Subtle breathing pulse for the status indicator
    val infiniteTransition = rememberInfiniteTransition(label = "dcPulse")
    val statusPulse by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "statusPulse",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = enterAlpha }
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.03f))
            // Subtle top-edge highlight
            .drawBehind {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = 20.dp.toPx(),
                    ),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                )
            }
            .padding(20.dp),
    ) {
        // ── Device identity row ────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Status dot
            Canvas(modifier = Modifier.size(8.dp)) {
                drawCircle(
                    color = StatusActive.copy(alpha = statusPulse),
                    radius = size.minDimension / 2f,
                )
                drawCircle(
                    color = StatusActive.copy(alpha = statusPulse * 0.3f),
                    radius = size.minDimension,
                )
            }
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$manufacturer $model",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }

            // Android version badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = androidVersion,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextTertiary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Thin separator ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        )
                    )
                ),
        )

        Spacer(Modifier.height(14.dp))

        // ── Sensor rows ────────────────────────────────────────────────
        sensors.forEachIndexed { index, sensor ->
            val rowDelay = 120L + index * 70L
            var rowVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(rowDelay)
                rowVisible = true
            }
            val rowAlpha by animateFloatAsState(
                if (rowVisible) 1f else 0f, tween(350), label = "sr$index"
            )
            val rowSlide by animateFloatAsState(
                if (rowVisible) 0f else 16f,
                spring(dampingRatio = 0.7f, stiffness = 200f),
                label = "srs$index"
            )

            SensorMetaRow(
                sensor = sensor,
                onClick = {
                    HapticUtils.tick(view)
                    onSensorTap(sensor.sensorType)
                },
                modifier = Modifier.graphicsLayer {
                    alpha = rowAlpha
                    translationY = rowSlide
                },
            )

            if (index < sensors.lastIndex) {
                Spacer(Modifier.height(6.dp))
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Thin separator ─────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        )
                    )
                ),
        )

        Spacer(Modifier.height(14.dp))

        // ── Bottom stats strip ─────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            StatMetric(
                label = "Rate",
                value = "%.0f Hz".format(animatedRate),
                color = CyanPrimary,
            )
            StatMetric(
                label = "Temp",
                value = if (batteryTemp != null) "%.1f°C".format(batteryTemp) else "—",
                color = if (batteryTemp != null && batteryTemp > 40f) DangerColor
                        else StatusActive,
            )
            StatMetric(
                label = "Uptime",
                value = formatUptime(uptimeSeconds),
                color = TextSecondary,
            )
            StatMetric(
                label = "Sensors",
                value = "${sensors.count { it.available }}/${sensors.size}",
                color = if (sensors.all { it.available }) StatusActive else DangerColor,
            )
        }
    }
}

// ── Sensor row ─────────────────────────────────────────────────────────────

@Composable
private fun SensorMetaRow(
    sensor: DeviceSensorInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(
                if (sensor.available) sensor.color.copy(alpha = 0.04f)
                else Color.Transparent
            )
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    if (sensor.available) sensor.color
                    else StatusInactive
                ),
        )

        Spacer(Modifier.width(10.dp))

        // Sensor type label
        Text(
            text = sensor.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (sensor.available) sensor.color else TextTertiary,
            modifier = Modifier.width(48.dp),
        )

        // Chip name (truncated)
        Text(
            text = sensor.meta?.name?.take(20) ?: "N/A",
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = if (sensor.available) Color.White.copy(alpha = 0.55f) else TextTertiary.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )

        // Max range
        if (sensor.available && sensor.meta != null) {
            Text(
                text = "±%.1f %s".format(sensor.meta.maxRange, sensor.unit),
                fontFamily = SensorValueFont,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

// ── Stat metric ────────────────────────────────────────────────────────────

@Composable
private fun StatMetric(
    label: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontFamily = SensorValueFont,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = TextTertiary,
            letterSpacing = 1.sp,
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun getBatteryTemp(context: Context): Float? {
    return try {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (temp > 0) temp / 10f else null
    } catch (_: Exception) {
        null
    }
}

private fun formatUptime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%02d:%02d".format(m, s)
}
