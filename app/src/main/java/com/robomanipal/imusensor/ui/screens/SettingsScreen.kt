package com.robomanipal.imusensor.ui.screens

import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robomanipal.imusensor.export.CSVExporter
import com.robomanipal.imusensor.export.JSONExporter
import com.robomanipal.imusensor.ui.components.GlassCard
import com.robomanipal.imusensor.ui.components.HapticUtils
import com.robomanipal.imusensor.ui.theme.*
import com.robomanipal.imusensor.viewmodel.SensorViewModel

@Composable
fun SettingsScreen(
    vm: SensorViewModel,
    modifier: Modifier = Modifier,
) {
    val delay by vm.sensorDelay.collectAsStateWithLifecycle()
    val rate  by vm.sampleRateHz.collectAsStateWithLifecycle()
    val ctx   = LocalContext.current
    val view  = LocalView.current

    val accelBuf by vm.accelBuffer.collectAsStateWithLifecycle()
    val gyroBuf  by vm.gyroBuffer.collectAsStateWithLifecycle()
    val magBuf   by vm.magBuffer.collectAsStateWithLifecycle()

    // ── Entrance ───────────────────────────────────────────────────
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60);  step = 1
        kotlinx.coroutines.delay(100); step = 2
        kotlinx.coroutines.delay(90);  step = 3
        kotlinx.coroutines.delay(80);  step = 4
    }

    val sa1 by animateFloatAsState(if (step >= 1) 1f else 0f, tween(400), label = "sa1")
    val so1 by animateFloatAsState(if (step >= 1) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so1")
    val sa2 by animateFloatAsState(if (step >= 2) 1f else 0f, tween(400), label = "sa2")
    val so2 by animateFloatAsState(if (step >= 2) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so2")
    val sa3 by animateFloatAsState(if (step >= 3) 1f else 0f, tween(400), label = "sa3")
    val so3 by animateFloatAsState(if (step >= 3) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so3")
    val sa4 by animateFloatAsState(if (step >= 4) 1f else 0f, tween(400), label = "sa4")
    val so4 by animateFloatAsState(if (step >= 4) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so4")

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

        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.graphicsLayer {
                alpha = sa1; translationY = so1
            },
        )

        Spacer(Modifier.height(20.dp))

        // ── Sensor Rate — Segmented Control ────────────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa1; translationY = so1 }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = CyanPrimary,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Sensor Rate",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyanPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyanPrimary.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "~%.0f Hz".format(rate),
                            fontFamily = SensorValueFont,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanPrimary,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Segmented control
                val options = listOf(
                    "Normal"  to SensorManager.SENSOR_DELAY_NORMAL,
                    "UI"      to SensorManager.SENSOR_DELAY_UI,
                    "Game"    to SensorManager.SENSOR_DELAY_GAME,
                    "Fastest" to SensorManager.SENSOR_DELAY_FASTEST,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    options.forEach { (label, value) ->
                        val isSelected = delay == value
                        val bg by animateColorAsState(
                            if (isSelected) CyanPrimary.copy(alpha = 0.2f) else Color.Transparent,
                            tween(250), label = "segBg$label",
                        )
                        val textColor by animateColorAsState(
                            if (isSelected) CyanPrimary else TextTertiary,
                            tween(250), label = "segText$label",
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .clickable {
                                    HapticUtils.tick(view)
                                    vm.updateSensorDelay(value)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Export ─────────────────────────────────────────────────────
        Text(
            text = "DATA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextTertiary,
            letterSpacing = 2.sp,
            modifier = Modifier
                .graphicsLayer { alpha = sa2 }
                .padding(bottom = 8.dp),
        )

        Box(modifier = Modifier.graphicsLayer { alpha = sa2; translationY = so2 }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = MagColor,
            ) {
                Text(
                    text = "Export Data",
                    style = MaterialTheme.typography.titleMedium,
                    color = MagColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Save current sensor buffer to Documents folder",
                    fontSize = 12.sp,
                    color = TextTertiary,
                )
                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ExportButton(
                        label = "CSV",
                        color = MagColor,
                        onClick = {
                            HapticUtils.click(view)
                            val path = CSVExporter.export(ctx, accelBuf, gyroBuf, magBuf)
                            Toast.makeText(ctx, "CSV saved:\n$path", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ExportButton(
                        label = "JSON",
                        color = MagColor,
                        onClick = {
                            HapticUtils.click(view)
                            val path = JSONExporter.export(ctx, accelBuf, gyroBuf, magBuf)
                            Toast.makeText(ctx, "JSON saved:\n$path", Toast.LENGTH_LONG).show()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Sensor Status ──────────────────────────────────────────
        Text(
            text = "SENSORS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextTertiary,
            letterSpacing = 2.sp,
            modifier = Modifier
                .graphicsLayer { alpha = sa3 }
                .padding(bottom = 8.dp),
        )

        Box(modifier = Modifier.graphicsLayer { alpha = sa3; translationY = so3 }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = OrientColor,
            ) {
                Text(
                    text = "Available Sensors",
                    style = MaterialTheme.typography.titleMedium,
                    color = OrientColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))

                SensorStatusRow("Accelerometer", vm.hasAccelerometer, AccelColor)
                SensorStatusRow("Gyroscope", vm.hasGyroscope, GyroColor)
                SensorStatusRow("Magnetometer", vm.hasMagnetometer, MagColor)
                SensorStatusRow("Rotation Vector", vm.hasRotationVector, OrientColor)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── About ──────────────────────────────────────────────────────
        Text(
            text = "ABOUT",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextTertiary,
            letterSpacing = 2.sp,
            modifier = Modifier
                .graphicsLayer { alpha = sa4 }
                .padding(bottom = 8.dp),
        )

        Box(modifier = Modifier.graphicsLayer { alpha = sa4; translationY = so4 }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = Color.White,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "IMU Sensor",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "v2.0.0",
                            fontSize = 12.sp,
                            color = CyanPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        CyanPrimary.copy(alpha = 0.15f),
                                        GyroColor.copy(alpha = 0.15f),
                                    )
                                )
                            )
                            .border(
                                0.5.dp,
                                Brush.linearGradient(
                                    listOf(
                                        CyanPrimary.copy(alpha = 0.3f),
                                        GyroColor.copy(alpha = 0.3f),
                                    )
                                ),
                                RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "RM",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary,
                            letterSpacing = 1.sp,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "RoboManipal IMU Research Suite\nUDP streaming · CSV/JSON export · Real-time fusion · Sensor comparison",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

// ── Private helpers ────────────────────────────────────────────────────────

@Composable
private fun ExportButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f),
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.height(48.dp),
    ) {
        Text(
            "Export $label",
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun SensorStatusRow(name: String, available: Boolean, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (available) color else StatusInactive),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            fontSize = 13.sp,
            color = if (available) Color.White.copy(alpha = 0.85f) else TextTertiary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (available) "Active" else "N/A",
            fontSize = 11.sp,
            color = if (available) color.copy(alpha = 0.7f) else StatusInactive,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
