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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Semi-circular gauge — no glow, clean sharp arc.
 *
 * @param displayValue  Raw degree value shown as text (e.g. -12.3)
 * @param arcFraction   0..1 fraction for arc fill (computed by caller)
 * @param label         Caption below the gauge
 * @param color         Accent color for arc and label
 * @param rangeLabel    Small range hint (e.g. "±90°")
 */
@Composable
fun AngleGauge(
    displayValue: Float,
    arcFraction: Float,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    rangeLabel: String = "",
) {
    val animatedDisplay by animateFloatAsState(
        targetValue   = displayValue,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 150f),
        label         = "gaugeDisplay",
    )
    val animatedFraction by animateFloatAsState(
        targetValue   = arcFraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 150f),
        label         = "gaugeFraction",
    )

    // Entrance fade
    var entered by remember { mutableStateOf(false) }
    val enterAlpha by animateFloatAsState(
        targetValue   = if (entered) 1f else 0f,
        animationSpec = tween(500),
        label         = "gaugeEnter",
    )
    LaunchedEffect(Unit) { entered = true }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.graphicsLayer { alpha = enterAlpha },
    ) {
        // The Canvas sizes itself to fill the column's available width,
        // constrained to a square via aspectRatio(1f).
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            ) {
                val sw    = 8.dp.toPx()
                val r     = (size.minDimension - sw) / 2f
                val cx    = size.width  / 2f
                val cy    = size.height / 2f
                val tl    = Offset(cx - r, cy - r)
                val arcSz = Size(r * 2f, r * 2f)

                val startAngle = 135f
                val totalSweep = 270f
                val valueSweep = totalSweep * animatedFraction

                // Tick marks
                drawTicks(cx, cy, r, sw, totalSweep, startAngle, color)

                // Background track
                drawArc(
                    color      = Color.White.copy(alpha = 0.07f),
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter  = false,
                    topLeft    = tl,
                    size       = arcSz,
                    style      = Stroke(sw, cap = StrokeCap.Round),
                )

                // Value arc — clean, no glow
                if (valueSweep > 0.5f) {
                    drawArc(
                        color      = color,
                        startAngle = startAngle,
                        sweepAngle = valueSweep,
                        useCenter  = false,
                        topLeft    = tl,
                        size       = arcSz,
                        style      = Stroke(sw, cap = StrokeCap.Round),
                    )

                    // Simple endpoint dot (no halo)
                    val endRad = Math.toRadians((startAngle + valueSweep).toDouble())
                    val dotX   = cx + r * cos(endRad).toFloat()
                    val dotY   = cy + r * sin(endRad).toFloat()
                    drawCircle(Color.White, 3.dp.toPx(), Offset(dotX, dotY))
                }
            }

            // Degree text centred in the arc
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.offset(y = 4.dp),
            ) {
                Text(
                    text       = if (displayValue >= 0) "+%.1f".format(animatedDisplay)
                                 else "%.1f".format(animatedDisplay),
                    fontFamily = SensorValueFont,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White.copy(alpha = 0.96f),
                )
                Text(
                    text       = "°",
                    fontSize   = 11.sp,
                    color      = color.copy(alpha = 0.65f),
                    lineHeight = 10.sp,
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text          = label,
            fontSize      = 11.sp,
            fontWeight    = FontWeight.Bold,
            color         = color,
            letterSpacing = 2.sp,
        )

        if (rangeLabel.isNotEmpty()) {
            Text(
                text          = rangeLabel,
                fontSize      = 9.sp,
                color         = Color.White.copy(alpha = 0.25f),
                letterSpacing = 0.5.sp,
            )
        }
    }
}

/** Subtle radial tick marks around the gauge arc — no glow. */
private fun DrawScope.drawTicks(
    cx: Float, cy: Float, r: Float,
    strokeWidth: Float, totalSweep: Float, startAngle: Float,
    color: Color,
    tickCount: Int = 9,
) {
    val outerR = r + strokeWidth / 2f + 1.5.dp.toPx()
    val innerR = outerR - strokeWidth * 0.65f

    for (i in 0..tickCount) {
        val frac    = i.toFloat() / tickCount
        val angle   = startAngle + totalSweep * frac
        val rad     = Math.toRadians(angle.toDouble())
        val c       = cos(rad).toFloat()
        val s       = sin(rad).toFloat()
        val isMajor = i % (tickCount / 2) == 0
        drawLine(
            color       = if (isMajor) color.copy(alpha = 0.35f)
                          else Color.White.copy(alpha = 0.09f),
            start       = Offset(cx + innerR * c, cy + innerR * s),
            end         = Offset(cx + outerR * c, cy + outerR * s),
            strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
            cap         = StrokeCap.Round,
        )
    }
}

/**
 * Row of three YPR gauges — each takes equal weight so they never overflow.
 *
 * Ranges:
 *   YAW   -180 … +180  → arc 0..1
 *   PITCH  -90 … +90   → arc 0..1
 *   ROLL  -180 … +180  → arc 0..1
 */
@Composable
fun YPRGauges(
    yaw: Float,
    pitch: Float,
    roll: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        AngleGauge(
            displayValue = yaw,
            arcFraction  = (yaw + 180f) / 360f,
            label        = "YAW",
            color        = AccelColor,
            rangeLabel   = "±180°",
            modifier     = Modifier.weight(1f),
        )
        AngleGauge(
            displayValue = pitch,
            arcFraction  = (pitch + 90f) / 180f,
            label        = "PITCH",
            color        = GyroColor,
            rangeLabel   = "±90°",
            modifier     = Modifier.weight(1f),
        )
        AngleGauge(
            displayValue = roll,
            arcFraction  = (roll + 180f) / 360f,
            label        = "ROLL",
            color        = MagColor,
            rangeLabel   = "±180°",
            modifier     = Modifier.weight(1f),
        )
    }
}
