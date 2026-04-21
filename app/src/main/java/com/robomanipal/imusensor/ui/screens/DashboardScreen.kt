package com.robomanipal.imusensor.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
    val uptime  by vm.uptimeSeconds.collectAsStateWithLifecycle()

    val accelBuf by vm.accelBuffer.collectAsStateWithLifecycle()
    val gyroBuf  by vm.gyroBuffer.collectAsStateWithLifecycle()
    val magBuf   by vm.magBuffer.collectAsStateWithLifecycle()

    val view = LocalView.current

    // Build sensor info list for DeviceCard
    val sensorInfoList = remember(vm) {
        listOf(
            DeviceSensorInfo("Accel", AccelColor, vm.hasAccelerometer, vm.accelMeta, "m/s²", "accel"),
            DeviceSensorInfo("Gyro",  GyroColor,  vm.hasGyroscope,     vm.gyroMeta,  "rad/s", "gyro"),
            DeviceSensorInfo("Mag",   MagColor,   vm.hasMagnetometer,  vm.magMeta,   "μT",    "mag"),
            DeviceSensorInfo("RotVec", OrientColor, vm.hasRotationVector, vm.rotVecMeta, "—", "orientation"),
        )
    }

    // ── Staggered entrance ─────────────────────────────────────────
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60);  step = 1
        kotlinx.coroutines.delay(180); step = 2
        kotlinx.coroutines.delay(120); step = 3
    }

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Header ─────────────────────────────────────────────────────
        val hdrAlpha by animateFloatAsState(if (step >= 1) 1f else 0f, tween(400), label = "hA")
        val hdrOff by animateFloatAsState(
            if (step >= 1) 0f else 20f,
            spring(dampingRatio = 0.7f, stiffness = 200f), label = "hO"
        )

        Text(
            text = "Dashboard",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.graphicsLayer { alpha = hdrAlpha; translationY = hdrOff },
        )

        Spacer(Modifier.height(16.dp))

        // ── Device Card Hero ───────────────────────────────────────────
        val heroAlpha by animateFloatAsState(if (step >= 1) 1f else 0f, tween(500), label = "heroA")
        val heroOff by animateFloatAsState(
            if (step >= 1) 0f else 35f,
            spring(dampingRatio = 0.6f, stiffness = 150f), label = "heroO"
        )

        Box(modifier = Modifier.graphicsLayer { alpha = heroAlpha; translationY = heroOff }) {
            DeviceCard(
                sensors = sensorInfoList,
                sampleRateHz = rate,
                uptimeSeconds = uptime,
                onSensorTap = { type ->
                    if (type == "orientation") {
                        onSensorTap("orientation")
                    } else {
                        onSensorTap(type)
                    }
                },
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Section header ─────────────────────────────────────────────
        val secAlpha by animateFloatAsState(if (step >= 2) 1f else 0f, tween(400), label = "secA")
        Text(
            text = "LIVE DATA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextTertiary,
            letterSpacing = 2.sp,
            modifier = Modifier
                .graphicsLayer { alpha = secAlpha }
                .padding(bottom = 10.dp),
        )

        // ── Bento Grid Row 1 (Accel + Gyro) ──────────────────────────
        val b1Alpha by animateFloatAsState(if (step >= 2) 1f else 0f, tween(500), label = "b1A")
        val b1Off by animateFloatAsState(
            if (step >= 2) 0f else 50f,
            spring(dampingRatio = 0.6f, stiffness = 160f), label = "b1O"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = b1Alpha; translationY = b1Off },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BentoSensorCard(
                title = "Accel",
                color = AccelColor,
                latest = accel,
                buffer = accelBuf,
                unit = "m/s²",
                onClick = {
                    HapticUtils.tick(view)
                    onSensorTap("accel")
                },
                modifier = Modifier.weight(1f),
            )
            BentoSensorCard(
                title = "Gyro",
                color = GyroColor,
                latest = gyro,
                buffer = gyroBuf,
                unit = "rad/s",
                onClick = {
                    HapticUtils.tick(view)
                    onSensorTap("gyro")
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(10.dp))

        // ── Bento Grid Row 2 (Mag + Orient preview) ──────────────────
        val b2Alpha by animateFloatAsState(if (step >= 3) 1f else 0f, tween(500), label = "b2A")
        val b2Off by animateFloatAsState(
            if (step >= 3) 0f else 50f,
            spring(dampingRatio = 0.6f, stiffness = 160f), label = "b2O"
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = b2Alpha; translationY = b2Off },
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BentoSensorCard(
                title = "Mag",
                color = MagColor,
                latest = mag,
                buffer = magBuf,
                unit = "μT",
                onClick = {
                    HapticUtils.tick(view)
                    onSensorTap("mag")
                },
                modifier = Modifier.weight(1f),
            )
            // Orient mini card with live cube
            GlassCard(
                modifier = Modifier.weight(1f),
                accentColor = OrientColor,
                onClick = {
                    HapticUtils.tick(view)
                    onSensorTap("orientation")
                },
            ) {
                Text(
                    text = "Orient",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrientColor,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
                OrientationCube(
                    yawDeg   = orient.yaw,
                    pitchDeg = orient.pitch,
                    rollDeg  = orient.roll,
                    edgeColor = OrientColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp),
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MiniYPR("Y", orient.yaw, AccelColor)
                    MiniYPR("P", orient.pitch, GyroColor)
                    MiniYPR("R", orient.roll, MagColor)
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

// ── Private components ────────────────────────────────────────────────────────

@Composable
private fun BentoSensorCard(
    title: String,
    color: Color,
    latest: SensorReading,
    buffer: List<SensorReading>,
    unit: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier,
        accentColor = color,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = unit,
                fontSize = 9.sp,
                color = TextTertiary,
            )
        }

        Spacer(Modifier.height(8.dp))

        SensorChart(
            dataX = buffer.map { it.x },
            dataY = buffer.map { it.y },
            dataZ = buffer.map { it.z },
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp),
            maxPoints = 80,
            showValuePills = false,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CompactAxis("X", latest.x, AxisXColor)
            CompactAxis("Y", latest.y, AxisYColor)
            CompactAxis("Z", latest.z, AxisZColor)
        }
    }
}

@Composable
private fun CompactAxis(axis: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = "%+.1f".format(value),
            fontFamily = SensorValueFont,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun MiniYPR(label: String, value: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 160f),
        label = "miniYPR",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = 0.6f),
        )
        Text(
            text = "%+.0f°".format(animated),
            fontFamily = SensorValueFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}
