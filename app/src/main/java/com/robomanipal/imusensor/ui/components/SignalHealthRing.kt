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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robomanipal.imusensor.ui.theme.*

/**
 * Apple Watch-style activity ring showing streaming health.
 *
 * Ring 1 (outer): Packet success rate (% of expected packets received)
 * Ring 2 (middle): Connection quality (rate stability)
 * Ring 3 (inner): Data freshness (green if recent, red if stale)
 */
@Composable
fun SignalHealthRing(
    packetRate: Float,       // 0-1, percentage of target rate achieved
    connectionQuality: Float, // 0-1, rate stability
    dataFreshness: Float,     // 0-1, how fresh the data is
    modifier: Modifier = Modifier,
) {
    val animRate by animateFloatAsState(
        packetRate.coerceIn(0f, 1f),
        spring(dampingRatio = 0.6f, stiffness = 100f),
        label = "ringRate",
    )
    val animQuality by animateFloatAsState(
        connectionQuality.coerceIn(0f, 1f),
        spring(dampingRatio = 0.6f, stiffness = 100f),
        label = "ringQuality",
    )
    val animFresh by animateFloatAsState(
        dataFreshness.coerceIn(0f, 1f),
        spring(dampingRatio = 0.6f, stiffness = 100f),
        label = "ringFresh",
    )

    // Glow pulse
    val infiniteTransition = rememberInfiniteTransition(label = "ringGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            tween(2000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "ringGlowAlpha",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val strokeW = 8.dp.toPx()
            val gap = 12.dp.toPx()

            val rings = listOf(
                Triple(animRate, StreamActiveColor, cx.coerceAtMost(cy) - strokeW / 2f),
                Triple(animQuality, OrientColor, cx.coerceAtMost(cy) - strokeW / 2f - gap),
                Triple(animFresh, MagColor, cx.coerceAtMost(cy) - strokeW / 2f - gap * 2f),
            )

            for ((fraction, color, radius) in rings) {
                val sweep = 360f * fraction
                val tl = Offset(cx - radius, cy - radius)
                val arcSize = Size(radius * 2f, radius * 2f)

                // Background track
                drawArc(
                    color = color.copy(alpha = 0.08f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = tl,
                    size = arcSize,
                    style = Stroke(strokeW, cap = StrokeCap.Round),
                )

                // Glow behind arc
                if (fraction > 0.01f) {
                    drawArc(
                        color = color.copy(alpha = 0.12f * glowAlpha),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(tl.x - 2.dp.toPx(), tl.y - 2.dp.toPx()),
                        size = Size(arcSize.width + 4.dp.toPx(), arcSize.height + 4.dp.toPx()),
                        style = Stroke(strokeW + 4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }

                // Active arc
                if (fraction > 0.01f) {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = tl,
                        size = arcSize,
                        style = Stroke(strokeW, cap = StrokeCap.Round),
                    )

                    // End cap glow
                    val endAngle = Math.toRadians((-90.0 + sweep).toDouble())
                    val endX = cx + radius * kotlin.math.cos(endAngle).toFloat()
                    val endY = cy + radius * kotlin.math.sin(endAngle).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.4f),
                        radius = 2.dp.toPx(),
                        center = Offset(endX, endY),
                    )
                }
            }
        }

        // Center label
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(animRate * 100).toInt()}%",
                fontFamily = SensorValueFont,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.95f),
            )
            Text(
                text = "HEALTH",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TextTertiary,
                letterSpacing = 2.sp,
            )
        }
    }
}
