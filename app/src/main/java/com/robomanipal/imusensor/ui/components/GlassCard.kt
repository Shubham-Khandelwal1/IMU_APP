package com.robomanipal.imusensor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.robomanipal.imusensor.ui.theme.CardHighlightTop
import com.robomanipal.imusensor.ui.theme.CardShadowAmbient
import com.robomanipal.imusensor.ui.theme.CardShadowSpot

/**
 * A frosted-glass card with layered depth (no glow).
 *
 * Depth is achieved through:
 *   1. Soft ambient shadow behind the card
 *   2. Inner top-edge highlight simulating a light source
 *   3. Gradient border fading from bright-top to dim-bottom
 *   4. Press animation: scale down + slight Y translation (card "pushes in")
 *   5. Optional accent glow for hero cards
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    cornerRadius: Dp = 22.dp,
    accentColor: Color = Color.White,
    accentGlow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "glassScale",
    )
    val transY by animateFloatAsState(
        targetValue = if (isPressed) 4f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "glassTransY",
    )
    val elevDp by animateFloatAsState(
        targetValue = if (isPressed) 2f else 8f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "glassElev",
    )

    // Gradient fill: subtle accent tint fading to near-transparent
    val glassFill = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.08f),
            accentColor.copy(alpha = 0.03f),
            Color.Transparent,
        ),
    )

    // Border: top-left highlight only — visionOS style
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.14f),
            Color.White.copy(alpha = 0.06f),
            Color.White.copy(alpha = 0.02f),
        ),
        start = Offset.Zero,
        end   = Offset(600f, 600f),
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = transY
            }
            // Accent glow behind card for hero elements
            .then(
                if (accentGlow) Modifier.drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.12f),
                                accentColor.copy(alpha = 0.04f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width / 2f, size.height * 0.3f),
                            radius = size.width * 0.7f,
                        ),
                    )
                } else Modifier
            )
            // Soft shadow for depth — ambient only, no colored glow
            .shadow(
                elevation    = elevDp.dp,
                shape        = shape,
                clip         = false,
                ambientColor = CardShadowAmbient,
                spotColor    = CardShadowSpot,
            )
            .clip(shape)
            .background(glassFill)
            // Inner top highlight: thin bright line at the top edge
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            CardHighlightTop,
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY   = 20.dp.toPx(),
                    ),
                )
            }
            .border(width = 0.5.dp, brush = borderBrush, shape = shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .padding(20.dp),
    ) {
        Column(content = content)
    }
}
