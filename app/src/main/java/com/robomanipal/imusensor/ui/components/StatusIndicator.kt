package com.robomanipal.imusensor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Animated pulsing dot that indicates status (active / streaming / inactive / error).
 */
@Composable
fun StatusIndicator(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    val dotAlpha = if (animate) pulse else 1f

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            // Outer glow
            drawCircle(color.copy(alpha = dotAlpha * 0.35f), radius = size.minDimension / 1.2f)
            // Inner core
            drawCircle(color.copy(alpha = dotAlpha), radius = size.minDimension / 2.5f)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = 0.85f),
        )
    }
}
