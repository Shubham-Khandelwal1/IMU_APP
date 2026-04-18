package com.robomanipal.imusensor.ui.screens

import android.content.Context
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robomanipal.imusensor.export.CSVExporter
import com.robomanipal.imusensor.export.JSONExporter
import com.robomanipal.imusensor.ui.components.GlassCard
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

    val accelBuf by vm.accelBuffer.collectAsStateWithLifecycle()
    val gyroBuf  by vm.gyroBuffer.collectAsStateWithLifecycle()
    val magBuf   by vm.magBuffer.collectAsStateWithLifecycle()

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
        )

        Spacer(Modifier.height(20.dp))

        // ── Sensor Configuration ───────────────────────────────────────
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
                        onClick = { vm.updateSensorDelay(value) },
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

        Spacer(Modifier.height(16.dp))

        // ── Export ─────────────────────────────────────────────────────
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

        Spacer(Modifier.height(16.dp))

        // ── About ──────────────────────────────────────────────────────
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

        Spacer(Modifier.height(100.dp))
    }
}
