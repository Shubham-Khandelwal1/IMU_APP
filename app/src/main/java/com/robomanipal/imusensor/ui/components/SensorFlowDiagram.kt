package com.robomanipal.imusensor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robomanipal.imusensor.ui.theme.*
import kotlin.math.*

/**
 * Minimal orbital sensor visualization.
 *
 * Design:
 *   - A thin outer ring with three colored arc segments (Accel / Gyro / Mag)
 *     that rotate slowly — showing the sensor pipeline is alive.
 *   - A subtle inner pulse ring that breathes with the sample rate.
 *   - Large, prominent YPR values in the center — the data IS the hero.
 *   - Three small labeled dots on the outer ring showing sensor status.
 *
 * The aesthetic is closer to a luxury watch face than a busy flow chart.
 */
@Composable
fun SensorFlowDiagram(
    yaw: Float,
    pitch: Float,
    roll: Float,
    sampleRateHz: Float,
    accelActive: Boolean = true,
    gyroActive: Boolean = true,
    magActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbital")

    // Slow orbit rotation
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (12000 / (sampleRateHz / 50f).coerceIn(0.5f, 2f)).toInt(),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orbitAngle",
    )

    // Inner pulse
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    // Arc sweep animation (arcs grow/shrink subtly)
    val arcBreath by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "arcBreath",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = minOf(cx, cy) * 0.88f
            val innerR = outerR * 0.72f
            val pulseR = outerR * 0.58f

            // ── Background ring (very faint) ──
            drawCircle(
                color = Color.White.copy(alpha = 0.03f),
                radius = outerR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )

            // ── Inner pulse ring ──
            drawCircle(
                color = FusionNodeColor.copy(alpha = 0.04f * pulse),
                radius = pulseR,
                center = Offset(cx, cy),
            )
            drawCircle(
                color = FusionNodeColor.copy(alpha = 0.08f * pulse),
                radius = pulseR,
                center = Offset(cx, cy),
                style = Stroke(width = 0.5.dp.toPx()),
            )

            // ── Three sensor arcs orbiting ──
            val arcWidth = 3.dp.toPx()
            val gapDeg = 14f
            val arcSweep = (360f / 3f - gapDeg) * arcBreath

            val sensors = listOf(
                Triple(AccelColor, accelActive, 0f),
                Triple(GyroColor, gyroActive, 120f),
                Triple(MagColor, magActive, 240f),
            )

            for ((color, active, baseAngle) in sensors) {
                val startAngle = baseAngle + orbitAngle - 90f
                val alpha = if (active) 0.7f else 0.1f

                // Glow arc (slightly larger, blurred feel)
                drawArc(
                    color = color.copy(alpha = alpha * 0.2f),
                    startAngle = startAngle,
                    sweepAngle = arcSweep,
                    useCenter = false,
                    topLeft = Offset(cx - outerR - 1.dp.toPx(), cy - outerR - 1.dp.toPx()),
                    size = Size((outerR + 1.dp.toPx()) * 2f, (outerR + 1.dp.toPx()) * 2f),
                    style = Stroke(width = arcWidth + 4.dp.toPx(), cap = StrokeCap.Round),
                )

                // Main arc
                drawArc(
                    color = color.copy(alpha = alpha),
                    startAngle = startAngle,
                    sweepAngle = arcSweep,
                    useCenter = false,
                    topLeft = Offset(cx - outerR, cy - outerR),
                    size = Size(outerR * 2f, outerR * 2f),
                    style = Stroke(width = arcWidth, cap = StrokeCap.Round),
                )

                // Leading dot (bright head of the arc)
                if (active) {
                    val headAngle = Math.toRadians((startAngle + arcSweep).toDouble()).toFloat()
                    val dotX = cx + outerR * cos(headAngle)
                    val dotY = cy + outerR * sin(headAngle)
                    drawCircle(
                        color = color.copy(alpha = 0.9f),
                        radius = 3.dp.toPx(),
                        center = Offset(dotX, dotY),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.5f),
                        radius = 1.5.dp.toPx(),
                        center = Offset(dotX, dotY),
                    )
                }
            }

            // ── Inner ring track ──
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = innerR,
                center = Offset(cx, cy),
                style = Stroke(width = 0.5.dp.toPx()),
            )

            // ── Subtle tick marks at 0°, 90°, 180°, 270° ──
            for (deg in listOf(0f, 90f, 180f, 270f)) {
                val angle = Math.toRadians((deg - 90.0)).toFloat()
                val r1 = outerR + 4.dp.toPx()
                val r2 = outerR + 8.dp.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.12f),
                    start = Offset(cx + r1 * cos(angle), cy + r1 * sin(angle)),
                    end = Offset(cx + r2 * cos(angle), cy + r2 * sin(angle)),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        // ── Center content: YPR readout ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Sensor status indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SensorDot("A", AccelColor, accelActive)
                SensorDot("G", GyroColor, gyroActive)
                SensorDot("M", MagColor, magActive)
            }

            Spacer(Modifier.height(14.dp))

            // YPR values — large and prominent
            FlowYPRValue("YAW", yaw, TextPrimary)
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                FlowYPRValue("PITCH", pitch, TextSecondary)
                FlowYPRValue("ROLL", roll, TextSecondary)
            }

            Spacer(Modifier.height(12.dp))

            // Rate indicator
            Text(
                text = "%.0f Hz".format(sampleRateHz),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = FusionNodeColor.copy(alpha = 0.5f),
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun SensorDot(label: String, color: Color, active: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Canvas(modifier = Modifier.size(5.dp)) {
            drawCircle(
                color = if (active) color else Color.White.copy(alpha = 0.15f),
            )
        }
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) color.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f),
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun FlowYPRValue(label: String, value: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 160f),
        label = "flowYPR",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            color = TextTertiary,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = "%+.1f°".format(animated),
            fontFamily = SensorValueFont,
            fontSize = if (label == "YAW") 28.sp else 20.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}
