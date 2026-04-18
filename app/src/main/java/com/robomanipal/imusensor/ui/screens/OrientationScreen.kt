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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robomanipal.imusensor.ui.components.*
import com.robomanipal.imusensor.ui.theme.*
import com.robomanipal.imusensor.viewmodel.SensorViewModel

/**
 * Premium Orientation screen: 3D cube + YPR gauges + data cards.
 * Full entrance animation cascade with staggered fade+slide.
 */
@Composable
fun OrientationScreen(
    vm: SensorViewModel,
    modifier: Modifier = Modifier,
) {
    val orient by vm.orientation.collectAsStateWithLifecycle()

    // ── Staggered entrance state ───────────────────────────────────────
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80);  step = 1
        kotlinx.coroutines.delay(140); step = 2
        kotlinx.coroutines.delay(120); step = 3
        kotlinx.coroutines.delay(120); step = 4
        kotlinx.coroutines.delay(100); step = 5
    }

    fun stepAlpha(s: Int) = if (step >= s) 1f else 0f
    fun stepOffset(s: Int) = if (step >= s) 0f else 40f

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D1A),
                        DarkBackground,
                        DarkBackground,
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header ─────────────────────────────────────────────────────
        val headAlpha by animateFloatAsState(stepAlpha(1), tween(400), label = "h")
        val headOff   by animateFloatAsState(stepOffset(1), tween(400, easing = EaseOutCubic), label = "ho")
        Text(
            text = "3D Orientation",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .graphicsLayer { translationY = headOff; alpha = headAlpha },
        )

        Spacer(Modifier.height(20.dp))

        // ── 3D Cube ────────────────────────────────────────────────────
        val cubeAlpha by animateFloatAsState(stepAlpha(2), tween(500), label = "ca")
        val cubeScale by animateFloatAsState(
            if (step >= 2) 1f else 0.88f,
            spring(dampingRatio = 0.65f, stiffness = 180f),
            label = "cs",
        )
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = cubeAlpha; scaleX = cubeScale; scaleY = cubeScale },
            accentColor = OrientColor,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Subtle background glow behind cube
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    OrientColor.copy(alpha = 0.07f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
                OrientationCube(
                    yawDeg   = orient.yaw,
                    pitchDeg = orient.pitch,
                    rollDeg  = orient.roll,
                    edgeColor = OrientColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── YPR Gauges ─────────────────────────────────────────────────
        val gaugeAlpha by animateFloatAsState(stepAlpha(3), tween(500), label = "ga")
        val gaugeOff  by animateFloatAsState(stepOffset(3), tween(450, easing = EaseOutCubic), label = "go")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = gaugeAlpha; translationY = gaugeOff },
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = Color.White.copy(alpha = 0.5f),
            ) {
                YPRGauges(
                    yaw   = orient.yaw,
                    pitch = orient.pitch,
                    roll  = orient.roll,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Euler Angles numeric readout ───────────────────────────────
        val eulerAlpha by animateFloatAsState(stepAlpha(4), tween(500), label = "ea")
        val eulerOff  by animateFloatAsState(stepOffset(4), tween(450, easing = EaseOutCubic), label = "eo")
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = eulerAlpha; translationY = eulerOff },
            accentColor = OrientColor,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Euler Angles",
                    style = MaterialTheme.typography.titleMedium,
                    color = OrientColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "degrees",
                    fontSize = 10.sp,
                    color = TextTertiary,
                    letterSpacing = 1.sp,
                )
            }
            Spacer(Modifier.height(14.dp))
            EulerRow("Yaw",   orient.yaw,   Color(0xFF00E5FF))
            EulerRow("Pitch", orient.pitch, Color(0xFFAA00FF))
            EulerRow("Roll",  orient.roll,  Color(0xFFFF6D00))
        }

        Spacer(Modifier.height(16.dp))

        // ── Quaternion ─────────────────────────────────────────────────
        val quatAlpha by animateFloatAsState(stepAlpha(5), tween(500), label = "qta")
        val quatOff  by animateFloatAsState(stepOffset(5), tween(450, easing = EaseOutCubic), label = "qto")
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = quatAlpha; translationY = quatOff },
            accentColor = OrientColor.copy(alpha = 0.4f),
        ) {
            Text(
                text = "Quaternion",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QuatValue("w", orient.quaternion.getOrElse(0) { 0f })
                QuatValue("x", orient.quaternion.getOrElse(1) { 0f })
                QuatValue("y", orient.quaternion.getOrElse(2) { 0f })
                QuatValue("z", orient.quaternion.getOrElse(3) { 0f })
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Rotation Matrix ────────────────────────────────────────────
        if (step >= 5) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(500)) + expandVertically(tween(500)),
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    accentColor = OrientColor.copy(alpha = 0.3f),
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            for (col in 0 until 3) {
                                val v = orient.rotationMatrix.getOrElse(row * 3 + col) { 0f }
                                Text(
                                    text = "%+.4f".format(v),
                                    fontFamily = SensorValueFont,
                                    fontSize = 12.sp,
                                    color = TextTertiary,
                                )
                            }
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
    val animated by animateFloatAsState(
        targetValue  = value,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label        = "euler$label",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Color badge
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(26.dp)
                .background(
                    Brush.verticalGradient(listOf(color, color.copy(alpha = 0.3f))),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                ),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text          = label,
            style         = MaterialTheme.typography.bodyMedium,
            color         = color.copy(alpha = 0.75f),
            fontWeight    = FontWeight.Medium,
            modifier      = Modifier.width(48.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            text          = "%+.2f°".format(animated),
            fontFamily    = SensorValueFont,
            fontSize      = 24.sp,
            fontWeight    = FontWeight.Bold,
            color         = Color.White.copy(alpha = 0.95f),
        )
    }
}

@Composable
private fun QuatValue(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = label,
            fontSize   = 10.sp,
            color      = TextTertiary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text       = "%+.4f".format(value),
            fontFamily = SensorValueFont,
            fontSize   = 13.sp,
            color      = TextSecondary,
        )
    }
}
