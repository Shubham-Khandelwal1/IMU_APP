package com.robomanipal.imusensor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robomanipal.imusensor.ui.theme.SensorValueFont

/**
 * A single semi-circular gauge that displays an angle value in degrees.
 * Features a gradient-filled arc, large degree readout, and label.
 */
@Composable
fun AngleGauge(
    value: Float,
    label: String,
    minValue: Float,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 120f),
        label = "gaugeVal",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                modifier = Modifier
                    .size(110.dp)
                    .padding(8.dp),
            ) {
                val strokeWidth = 8.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                val topLeft = Offset(
                    (size.width - radius * 2) / 2f,
                    (size.height - radius * 2) / 2f,
                )
                val arcSize = Size(radius * 2, radius * 2)

                val startAngle = 135f
                val totalSweep = 270f

                // Track arc (background)
                drawArc(
                    color = Color.White.copy(alpha = 0.07f),
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Value arc (filled)
                val fraction = ((animated - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
                val valueSweep = totalSweep * fraction

                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            color.copy(alpha = 0.6f),
                            color,
                            color.copy(alpha = 0.8f),
                        ),
                    ),
                    startAngle = startAngle,
                    sweepAngle = valueSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )

                // Glow on the value arc
                drawArc(
                    color = color.copy(alpha = 0.2f),
                    startAngle = startAngle,
                    sweepAngle = valueSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth + 6.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Degree text centred inside the arc
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "%.1f".format(animated),
                    fontFamily = SensorValueFont,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.95f),
                )
                Text(
                    text = "°",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = color.copy(alpha = 0.7f),
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color.copy(alpha = 0.85f),
            letterSpacing = 1.sp,
        )
    }
}

/**
 * Row of three YPR gauges.
 */
@Composable
fun YPRGauges(
    yaw: Float,
    pitch: Float,
    roll: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AngleGauge(
            value    = (yaw + 360f) % 360f,   // normalise to 0-360
            label    = "YAW",
            minValue = 0f,
            maxValue = 360f,
            color    = Color(0xFF00E5FF),
        )
        AngleGauge(
            value    = pitch + 90f,            // shift so -90..+90 → 0..180
            label    = "PITCH",
            minValue = 0f,
            maxValue = 180f,
            color    = Color(0xFFAA00FF),
        )
        AngleGauge(
            value    = roll + 180f,            // shift so -180..+180 → 0..360
            label    = "ROLL",
            minValue = 0f,
            maxValue = 360f,
            color    = Color(0xFFFF6D00),
        )
    }
}
