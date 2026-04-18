package com.robomanipal.imusensor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.robomanipal.imusensor.ui.theme.AxisXColor
import com.robomanipal.imusensor.ui.theme.AxisYColor
import com.robomanipal.imusensor.ui.theme.AxisZColor
import com.robomanipal.imusensor.ui.theme.CyanPrimary
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 3-D wireframe cube that rotates according to Euler angles (yaw, pitch, roll).
 * Drawn entirely on Canvas with perspective projection and colored edge glow.
 */
@Composable
fun OrientationCube(
    yawDeg: Float,
    pitchDeg: Float,
    rollDeg: Float,
    modifier: Modifier = Modifier,
    edgeColor: Color = CyanPrimary,
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val cubeSize = size.minDimension * 0.28f

        // Vertices of a unit cube centred at origin (-1..+1)
        val verts = listOf(
            floatArrayOf(-1f, -1f, -1f), floatArrayOf( 1f, -1f, -1f),
            floatArrayOf( 1f,  1f, -1f), floatArrayOf(-1f,  1f, -1f),
            floatArrayOf(-1f, -1f,  1f), floatArrayOf( 1f, -1f,  1f),
            floatArrayOf( 1f,  1f,  1f), floatArrayOf(-1f,  1f,  1f),
        )

        // Edges (vertex index pairs)
        val edges = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 0,   // back face
            4 to 5, 5 to 6, 6 to 7, 7 to 4,   // front face
            0 to 4, 1 to 5, 2 to 6, 3 to 7,   // connecting
        )

        // Axis lines (from origin, length 1.5)
        val axisLen = 1.6f

        // ── Rotation matrices ──────────────────────────────────────────
        val yaw   = Math.toRadians(yawDeg.toDouble())
        val pitch = Math.toRadians(pitchDeg.toDouble())
        val roll  = Math.toRadians(rollDeg.toDouble())

        fun rotatePoint(p: FloatArray): FloatArray {
            var x = p[0]; var y = p[1]; var z = p[2]

            // Roll  (around Z)
            val cr = cos(roll).toFloat(); val sr = sin(roll).toFloat()
            val x1 = x * cr - y * sr;    val y1 = x * sr + y * cr
            x = x1; y = y1

            // Pitch (around X)
            val cp = cos(pitch).toFloat(); val sp = sin(pitch).toFloat()
            val y2 = y * cp - z * sp;     val z2 = y * sp + z * cp
            y = y2; z = z2

            // Yaw   (around Y)
            val cy2 = cos(yaw).toFloat(); val sy = sin(yaw).toFloat()
            val x3 = x * cy2 + z * sy;   val z3 = -x * sy + z * cy2
            x = x3; z = z3

            return floatArrayOf(x, y, z)
        }

        // Perspective projection
        val fov = 4.5f
        fun project(p: FloatArray): Offset {
            val d = fov / (fov + p[2])
            return Offset(cx + p[0] * cubeSize * d, cy - p[1] * cubeSize * d)
        }

        // ── Draw cube edges ─────────────────────────────────────────────
        val projected = verts.map { project(rotatePoint(it)) }
        val glowStroke = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val lineStroke = Stroke(width = 1.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)

        for ((a, b) in edges) {
            // Glow
            drawLine(edgeColor.copy(alpha = 0.2f), projected[a], projected[b], strokeWidth = glowStroke.width)
            // Core
            drawLine(edgeColor.copy(alpha = 0.8f), projected[a], projected[b], strokeWidth = lineStroke.width)
        }

        // ── Draw axis indicators ────────────────────────────────────────
        val origin = project(rotatePoint(floatArrayOf(0f, 0f, 0f)))
        val xEnd   = project(rotatePoint(floatArrayOf(axisLen, 0f, 0f)))
        val yEnd   = project(rotatePoint(floatArrayOf(0f, axisLen, 0f)))
        val zEnd   = project(rotatePoint(floatArrayOf(0f, 0f, axisLen)))

        val axisStroke = 2.dp.toPx()
        drawLine(AxisXColor, origin, xEnd, strokeWidth = axisStroke)
        drawLine(AxisYColor, origin, yEnd, strokeWidth = axisStroke)
        drawLine(AxisZColor, origin, zEnd, strokeWidth = axisStroke)

        // Axis end dots
        val dotR = 3.dp.toPx()
        drawCircle(AxisXColor, dotR, xEnd)
        drawCircle(AxisYColor, dotR, yEnd)
        drawCircle(AxisZColor, dotR, zEnd)
    }
}
