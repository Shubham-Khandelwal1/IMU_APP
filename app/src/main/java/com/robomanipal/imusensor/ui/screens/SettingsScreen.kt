package com.robomanipal.imusensor.ui.screens

import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    }

    val sa1 by animateFloatAsState(if (step >= 1) 1f else 0f, tween(400), label = "sa1")
    val so1 by animateFloatAsState(if (step >= 1) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so1")
    val sa2 by animateFloatAsState(if (step >= 2) 1f else 0f, tween(400), label = "sa2")
    val so2 by animateFloatAsState(if (step >= 2) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so2")
    val sa3 by animateFloatAsState(if (step >= 3) 1f else 0f, tween(400), label = "sa3")
    val so3 by animateFloatAsState(if (step >= 3) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so3")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(16.dp))

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

        // ── Sensor Configuration ───────────────────────────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa1; translationY = so1 }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = CyanPrimary,
            ) {
                Text(
                    text = "Sensor Rate",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyanPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Current: ~%.0f Hz".format(rate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))

                val options = listOf(
                    "Normal"  to SensorManager.SENSOR_DELAY_NORMAL,
                    "UI"      to SensorManager.SENSOR_DELAY_UI,
                    "Game"    to SensorManager.SENSOR_DELAY_GAME,
                    "Fastest" to SensorManager.SENSOR_DELAY_FASTEST,
                )

                options.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = delay == value,
                            onClick = {
                                HapticUtils.tick(view)
                                vm.updateSensorDelay(value)
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CyanPrimary,
                                unselectedColor = TextTertiary,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (delay == value) Color.White else TextSecondary,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Export ─────────────────────────────────────────────────────
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
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            HapticUtils.click(view)
                            val path = CSVExporter.export(ctx, accelBuf, gyroBuf, magBuf)
                            Toast.makeText(ctx, "CSV saved:\n$path", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MagColor.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Export CSV", color = MagColor, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            HapticUtils.click(view)
                            val path = JSONExporter.export(ctx, accelBuf, gyroBuf, magBuf)
                            Toast.makeText(ctx, "JSON saved:\n$path", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MagColor.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Export JSON", color = MagColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── About ──────────────────────────────────────────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa3; translationY = so3 }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = Color.White,
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "IMU Sensor v1.0.0",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Built for testing hobby IMUs against phone-grade sensors.\nUDP streaming · CSV/JSON export · Real-time visualization",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    lineHeight = 18.sp,
                )
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}
