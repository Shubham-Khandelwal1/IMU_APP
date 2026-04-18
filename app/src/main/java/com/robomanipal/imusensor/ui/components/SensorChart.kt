package com.robomanipal.imusensor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.robomanipal.imusensor.ui.theme.AxisXColor
import com.robomanipal.imusensor.ui.theme.AxisYColor
import com.robomanipal.imusensor.ui.theme.AxisZColor

/**
 * Real-time 3-axis line chart drawn on Canvas.
 * Features smooth cubic-bezier interpolation, gradient glow, and subtle grid.
 *
 * @param dataX  X-axis rolling data
 * @param dataY  Y-axis rolling data
 * @param dataZ  Z-axis rolling data
 * @param maxPoints  Total width of the chart in data points (sets the x-axis scale)
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
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val pad = 4.dp.toPx()

        // ── Auto-range Y axis ──────────────────────────────────────────
        val all = dataX + dataY + dataZ
        val minVal = (all.minOrNull() ?: -10f) - 0.5f
        val maxVal = (all.maxOrNull() ?: 10f) + 0.5f
        val range = (maxVal - minVal).coerceAtLeast(1f)

        fun mapY(v: Float): Float = pad + (h - 2 * pad) * (1f - (v - minVal) / range)

        // ── Grid ───────────────────────────────────────────────────────
        if (showGrid) {
            val gridColor = Color.White.copy(alpha = 0.04f)
            for (i in 0..4) {
                val y = pad + (h - 2 * pad) * i / 4f
                drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            // Zero line (slightly brighter)
            val zeroY = mapY(0f)
            if (zeroY in pad..(h - pad)) {
                drawLine(
                    Color.White.copy(alpha = 0.08f),
                    Offset(0f, zeroY),
                    Offset(w, zeroY),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f)),
                )
            }
        }

        // ── Helper: build a smooth cubic path ──────────────────────────
        fun buildPath(data: List<Float>): Path? {
            if (data.size < 2) return null
            val path = Path()
            val step = (w - 2 * pad) / (maxPoints - 1).coerceAtLeast(1)
            val offset = maxPoints - data.size

            data.forEachIndexed { i, v ->
                val x = pad + (offset + i) * step
                val y = mapY(v)

                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    // Cubic bezier using previous and current points
                    val prevX = pad + (offset + i - 1) * step
                    val prevY = mapY(data[i - 1])
                    val cpx = (prevX + x) / 2f
                    path.cubicTo(cpx, prevY, cpx, y, x, y)
                }
            }
            return path
        }

        // ── Draw each series (glow → line) ─────────────────────────────
        fun drawSeries(data: List<Float>, color: Color) {
            val path = buildPath(data) ?: return
            // Glow pass (wide + transparent)
            drawPath(
                path,
                color.copy(alpha = 0.25f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            // Sharp pass
            drawPath(
                path,
                color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }

        drawSeries(dataX, colorX)
        drawSeries(dataY, colorY)
        drawSeries(dataZ, colorZ)
    }
}
