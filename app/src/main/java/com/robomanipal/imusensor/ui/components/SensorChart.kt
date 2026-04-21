package com.robomanipal.imusensor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robomanipal.imusensor.ui.theme.AxisXColor
import com.robomanipal.imusensor.ui.theme.AxisYColor
import com.robomanipal.imusensor.ui.theme.AxisZColor
import com.robomanipal.imusensor.ui.theme.SensorValueFont

/**
 * Premium 3-axis line chart with:
 * - Gradient fill under each line
 * - Value pills at the right edge showing current values
 * - Smooth cubic-bezier interpolation
 * - Subtle grid with axis range labels
 */
@Composable
fun SensorChart(
    dataX: List<Float>,
    dataY: List<Float>,
    dataZ: List<Float>,
    modifier: Modifier = Modifier,
    maxPoints: Int = 200,
    colorX: Color = AxisXColor,
    colorY: Color = AxisYColor,
    colorZ: Color = AxisZColor,
    showGrid: Boolean = true,
    showValuePills: Boolean = true,
    showAxisLabels: Boolean = false,
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 4.dp.toPx()
        val rightPad = if (showValuePills) 44.dp.toPx() else pad

        // ── Auto-range Y axis ──────────────────────────────────────────
        val all = dataX + dataY + dataZ
        val minVal = (all.minOrNull() ?: -10f) - 0.5f
        val maxVal = (all.maxOrNull() ?: 10f) + 0.5f
        val range = (maxVal - minVal).coerceAtLeast(1f)

        fun mapY(v: Float): Float = pad + (h - 2 * pad) * (1f - (v - minVal) / range)

        // ── Grid ───────────────────────────────────────────────────────
        if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.035f)
            for (i in 0..4) {
                val y = pad + (h - 2 * pad) * i / 4f
                drawLine(gridColor, Offset(0f, y), Offset(w - rightPad, y), strokeWidth = 1f)
            }
            // Zero line (slightly brighter)
            val zeroY = mapY(0f)
            if (zeroY in pad..(h - pad)) {
                drawLine(
                    Color.White.copy(alpha = 0.07f),
                    Offset(0f, zeroY),
                    Offset(w - rightPad, zeroY),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
            }
        }

        // ── Axis labels ────────────────────────────────────────────────
        if (showAxisLabels) {
            val labelStyle = TextStyle(
                fontSize = 8.sp,
                color = Color.White.copy(alpha = 0.25f),
                fontWeight = FontWeight.Medium,
            )
            val topLabel = textMeasurer.measure("%.1f".format(maxVal), labelStyle)
            val botLabel = textMeasurer.measure("%.1f".format(minVal), labelStyle)
            drawText(topLabel, topLeft = Offset(2.dp.toPx(), pad))
            drawText(botLabel, topLeft = Offset(2.dp.toPx(), h - pad - botLabel.size.height))
        }

        // ── Helper: build a smooth cubic path ──────────────────────────
        fun buildPath(data: List<Float>): Path? {
            if (data.size < 2) return null
            val path = Path()
            val chartW = w - pad - rightPad
            val step = chartW / (maxPoints - 1).coerceAtLeast(1)
            val offset = maxPoints - data.size

            data.forEachIndexed { i, v ->
                val x = pad + (offset + i) * step
                val y = mapY(v)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    val prevX = pad + (offset + i - 1) * step
                    val prevY = mapY(data[i - 1])
                    val cpx = (prevX + x) / 2f
                    path.cubicTo(cpx, prevY, cpx, y, x, y)
                }
            }
            return path
        }

        // ── Draw each series ─────────────────────────────────────────
        fun drawSeries(data: List<Float>, color: Color) {
            val path = buildPath(data) ?: return
            val chartW = w - pad - rightPad
            val step = chartW / (maxPoints - 1).coerceAtLeast(1)
            val offset = maxPoints - data.size

            // Gradient fill under the line
            val fillPath = Path().apply {
                addPath(path)
                val lastX = pad + (offset + data.size - 1) * step
                val firstX = pad + offset * step
                lineTo(lastX, h)
                lineTo(firstX, h)
                close()
            }

            clipRect(0f, 0f, w - rightPad, h) {
                drawPath(
                    fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            color.copy(alpha = 0.15f),
                            color.copy(alpha = 0.02f),
                            Color.Transparent,
                        ),
                    ),
                )

                // Line
                drawPath(
                    path,
                    color,
                    style = Stroke(
                        width = 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        drawSeries(dataX, colorX)
        drawSeries(dataY, colorY)
        drawSeries(dataZ, colorZ)

        // ── Value pills ────────────────────────────────────────────────
        if (showValuePills && (dataX.isNotEmpty() || dataY.isNotEmpty() || dataZ.isNotEmpty())) {
            val pillX = w - rightPad + 4.dp.toPx()
            val pillW = rightPad - 6.dp.toPx()
            val pillH = 14.dp.toPx()
            val pillR = 4.dp.toPx()

            fun drawPill(data: List<Float>, color: Color, yOffset: Float) {
                if (data.isEmpty()) return
                val lastVal = data.last()
                val pillY = mapY(lastVal).coerceIn(pad, h - pad - pillH) + yOffset

                // Pill background
                val pillPath = Path().apply {
                    addRoundRect(RoundRect(Rect(pillX, pillY, pillX + pillW, pillY + pillH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillR)))
                }
                drawPath(pillPath, color.copy(alpha = 0.2f))
                drawPath(pillPath, color.copy(alpha = 0.5f), style = Stroke(0.5.dp.toPx()))

                // Value text
                val text = textMeasurer.measure(
                    "%.1f".format(lastVal),
                    TextStyle(
                        fontSize = 8.sp,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = SensorValueFont,
                    ),
                )
                drawText(
                    text,
                    topLeft = Offset(
                        pillX + (pillW - text.size.width) / 2f,
                        pillY + (pillH - text.size.height) / 2f,
                    ),
                )

                // Connecting dot
                val dotY = mapY(lastVal)
                drawCircle(color, 2.5.dp.toPx(), Offset(w - rightPad, dotY))
                drawCircle(Color.White.copy(alpha = 0.6f), 1.dp.toPx(), Offset(w - rightPad, dotY))
            }

            drawPill(dataX, colorX, -pillH * 0.6f)
            drawPill(dataY, colorY, 0f)
            drawPill(dataZ, colorZ, pillH * 0.6f)
        }
    }
}
