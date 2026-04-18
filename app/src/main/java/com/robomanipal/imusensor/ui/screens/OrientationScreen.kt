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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robomanipal.imusensor.ui.components.*
import com.robomanipal.imusensor.ui.theme.*
import com.robomanipal.imusensor.viewmodel.SensorViewModel

/**
 * Dedicated Orientation screen with the 3D cube and YPR gauges.
 * This is the "star" screen — visually the most impressive.
 */
@Composable
fun OrientationScreen(
    vm: SensorViewModel,
    modifier: Modifier = Modifier,
) {
    val orient by vm.orientation.collectAsStateWithLifecycle()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Orientation",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(20.dp))

        // ── 3D Cube ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600)) + scaleIn(initialScale = 0.85f, animationSpec = tween(600)),
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = OrientColor,
            ) {
                OrientationCube(
                    yawDeg   = orient.yaw,
                    pitchDeg = orient.pitch,
                    rollDeg  = orient.roll,
                    edgeColor = OrientColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── YPR Gauges ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, delayMillis = 150)) + slideInVertically(
                initialOffsetY = { it / 4 },
                animationSpec = tween(600, delayMillis = 150),
            ),
        ) {
            YPRGauges(
                yaw   = orient.yaw,
                pitch = orient.pitch,
                roll  = orient.roll,
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Euler angles numeric readout ───────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500, delayMillis = 300)),
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = OrientColor,
            ) {
                Text(
                    text = "Euler Angles",
                    style = MaterialTheme.typography.titleMedium,
                    color = OrientColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                EulerRow("Yaw",   orient.yaw,   AccelColor)
                EulerRow("Pitch", orient.pitch,  GyroColor)
                EulerRow("Roll",  orient.roll,   MagColor)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Quaternion ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500, delayMillis = 400)),
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = OrientColor,
            ) {
                Text(
                    text = "Quaternion",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    QuatValue("w", orient.quaternion[0])
                    QuatValue("x", orient.quaternion[1])
                    QuatValue("y", orient.quaternion[2])
                    QuatValue("z", orient.quaternion[3])
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Rotation Matrix ────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500, delayMillis = 500)),
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = OrientColor,
            ) {
                Text(
                    text = "Rotation Matrix",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                for (row in 0 until 3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        for (col in 0 until 3) {
                            Text(
                                text = "%+.4f".format(orient.rotationMatrix[row * 3 + col]),
                                fontFamily = SensorValueFont,
                                fontSize = 13.sp,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

@Composable
private fun EulerRow(label: String, value: Float, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = color.copy(alpha = 0.8f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(56.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "%+.2f°".format(value),
            fontFamily = SensorValueFont,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.95f),
        )
    }
}

@Composable
private fun QuatValue(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextTertiary,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = "%+.4f".format(value),
            fontFamily = SensorValueFont,
            fontSize = 14.sp,
            color = TextSecondary,
        )
    }
}
