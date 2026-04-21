package com.robomanipal.imusensor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robomanipal.imusensor.ui.theme.*
import kotlin.math.*

/**
 * Aircraft-style Artificial Horizon indicator.
 *
 * Shows:
 * - Sky/ground split that tilts with roll and shifts vertically with pitch
 * - Pitch ladder lines with degree markers
 * - Roll indicator arc at the top
 * - Center aircraft reference symbol
 * - Heading compass ring around the edge that rotates with yaw
 * - Subtle grid lines on the ground portion
 */
@Composable
fun ArtificialHorizon(
    yawDeg: Float,
    pitchDeg: Float,
    rollDeg: Float,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    // Smooth animation
    val animYaw by animateFloatAsState(
        yawDeg, spring(dampingRatio = 0.7f, stiffness = 120f), label = "ahYaw"
    )
    val animPitch by animateFloatAsState(
        pitchDeg, spring(dampingRatio = 0.7f, stiffness = 120f), label = "ahPitch"
    )
    val animRoll by animateFloatAsState(
        rollDeg, spring(dampingRatio = 0.7f, stiffness = 120f), label = "ahRoll"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy) * 0.88f

        // Clip to circle
        val clipPath = Path().apply {
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
        }

        clipPath(clipPath) {
            // ── Sky/Ground with roll rotation and pitch shift ──
            val rollRad = Math.toRadians(animRoll.toDouble()).toFloat()
            val pitchPixels = (animPitch / 90f) * r * 1.5f  // pitch -> vertical pixel shift

            rotate(degrees = -animRoll, pivot = Offset(cx, cy)) {
                // Sky gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(HorizonSkyTop, HorizonSkyBottom),
                        startY = cy - r * 2f + pitchPixels,
                        endY = cy + pitchPixels,
                    ),
                    topLeft = Offset(cx - r * 2f, cy - r * 2f + pitchPixels),
                    size = Size(r * 4f, r * 2f),
                )

                // Ground gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(HorizonGroundTop, HorizonGroundBottom),
                        startY = cy + pitchPixels,
                        endY = cy + r * 2f + pitchPixels,
                    ),
                    topLeft = Offset(cx - r * 2f, cy + pitchPixels),
                    size = Size(r * 4f, r * 2f),
                )

                // Horizon line
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(cx - r * 2f, cy + pitchPixels),
                    end = Offset(cx + r * 2f, cy + pitchPixels),
                    strokeWidth = 2.dp.toPx(),
                )

                // ── Pitch ladder ──
                val pitchLineHalfWidth = r * 0.3f
                for (deg in listOf(-30, -20, -10, 10, 20, 30)) {
                    val py = cy + pitchPixels - (deg / 90f) * r * 1.5f
                    val hw = if (deg % 20 == 0) pitchLineHalfWidth else pitchLineHalfWidth * 0.6f
                    val alpha = if (deg % 20 == 0) 0.5f else 0.3f

                    drawLine(
                        color = Color.White.copy(alpha = alpha),
                        start = Offset(cx - hw, py),
                        end = Offset(cx + hw, py),
                        strokeWidth = 1.dp.toPx(),
                    )

                    // Degree labels
                    if (deg % 20 == 0) {
                        val label = textMeasurer.measure(
                            "${abs(deg)}",
                            TextStyle(
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                        drawText(
                            label,
                            topLeft = Offset(
                                cx - hw - label.size.width - 4.dp.toPx(),
                                py - label.size.height / 2f,
                            ),
                        )
                        drawText(
                            label,
                            topLeft = Offset(
                                cx + hw + 4.dp.toPx(),
                                py - label.size.height / 2f,
                            ),
                        )
                    }
                }

                // Ground grid lines
                for (i in 1..4) {
                    val gy = cy + pitchPixels + i * r * 0.3f
                    drawLine(
                        color = Color.White.copy(alpha = 0.04f),
                        start = Offset(cx - r * 2f, gy),
                        end = Offset(cx + r * 2f, gy),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                }
            }
        }

        // ── Outer ring ──
        drawCircle(
            color = Color.White.copy(alpha = 0.15f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
        )

        // ── Heading compass ring ──
        val compassR = r + 14.dp.toPx()
        val headings = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)
        val tickR1 = r + 3.dp.toPx()
        val tickR2 = r + 10.dp.toPx()

        // Small ticks every 30°
        for (i in 0 until 12) {
            val deg = i * 30f - animYaw
            val rad = Math.toRadians(deg.toDouble() - 90.0).toFloat()
            val isMajor = i % 3 == 0
            val tr1 = if (isMajor) tickR1 else tickR1 + 2.dp.toPx()
            drawLine(
                color = Color.White.copy(alpha = if (isMajor) 0.5f else 0.2f),
                start = Offset(cx + tr1 * cos(rad), cy + tr1 * sin(rad)),
                end = Offset(cx + tickR2 * cos(rad), cy + tickR2 * sin(rad)),
                strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // Cardinal labels
        for ((label, heading) in headings) {
            val deg = heading - animYaw
            val rad = Math.toRadians(deg.toDouble() - 90.0).toFloat()
            val labelColor = when (label) {
                "N" -> Color(0xFFEF4444)
                else -> Color.White.copy(alpha = 0.6f)
            }
            val text = textMeasurer.measure(
                label,
                TextStyle(
                    fontSize = 10.sp,
                    color = labelColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                ),
            )
            drawText(
                text,
                topLeft = Offset(
                    cx + compassR * cos(rad) - text.size.width / 2f,
                    cy + compassR * sin(rad) - text.size.height / 2f,
                ),
            )
        }

        // ── Roll indicator arc (top) ──
        val rollArcR = r - 6.dp.toPx()
        // Triangle pointer at current roll
        val pointerAngle = Math.toRadians(-90.0).toFloat()
        val pointerR = r + 1.dp.toPx()
        val ps = 5.dp.toPx()
        val px = cx + pointerR * cos(pointerAngle)
        val py = cy + pointerR * sin(pointerAngle)
        val pointerPath = Path().apply {
            moveTo(px, py)
            moveTo(px - ps * 0.6f, py - ps)
            lineTo(px, py)
            lineTo(px + ps * 0.6f, py - ps)
        }
        drawPath(
            pointerPath,
            color = Color.White.copy(alpha = 0.7f),
            style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Roll angle ticks
        for (deg in listOf(-60f, -45f, -30f, -20f, -10f, 0f, 10f, 20f, 30f, 45f, 60f)) {
            val angle = Math.toRadians((-90.0 + deg).toDouble()).toFloat()
            val r1 = rollArcR - 4.dp.toPx()
            val r2 = rollArcR + 2.dp.toPx()
            val isMajor = deg % 30f == 0f
            drawLine(
                color = Color.White.copy(alpha = if (isMajor) 0.4f else 0.2f),
                start = Offset(cx + r1 * cos(angle), cy + r1 * sin(angle)),
                end = Offset(cx + r2 * cos(angle), cy + r2 * sin(angle)),
                strokeWidth = if (isMajor) 1.5.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // ── Center aircraft symbol ──
        val wingW = r * 0.28f
        val wingH = 2.dp.toPx()

        // Left wing
        drawLine(
            color = Color.White,
            start = Offset(cx - wingW, cy),
            end = Offset(cx - wingW * 0.3f, cy),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // Right wing
        drawLine(
            color = Color.White,
            start = Offset(cx + wingW * 0.3f, cy),
            end = Offset(cx + wingW, cy),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        // Center dot
        drawCircle(
            color = Color.White,
            radius = 4.dp.toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx()),
        )
        // Wing tips down
        drawLine(
            color = Color.White,
            start = Offset(cx - wingW, cy),
            end = Offset(cx - wingW, cy + 8.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(cx + wingW, cy),
            end = Offset(cx + wingW, cy + 8.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // ── Outer subtle glow ring ──
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.03f),
                ),
                center = Offset(cx, cy),
                radius = r * 1.2f,
            ),
            radius = r * 1.2f,
            center = Offset(cx, cy),
        )
    }
}
