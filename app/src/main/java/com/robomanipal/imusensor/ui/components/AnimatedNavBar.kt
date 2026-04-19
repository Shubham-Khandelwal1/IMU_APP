package com.robomanipal.imusensor.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robomanipal.imusensor.ui.theme.CyanPrimary
import com.robomanipal.imusensor.ui.theme.TextSecondary

data class NavItem(
    val icon: ImageVector,
    val label: String,
)

/**
 * Bottom navigation with a **liquid-glass** sliding pill indicator.
 *
 * The pill simulates liquid glass through:
 *   1. Layered translucent fills (depth)
 *   2. Travelling specular highlight that shifts as the pill slides
 *   3. Rim lighting — bright on top edge, dim on bottom
 *   4. Inner bottom shadow for grounded depth
 *   5. Subtle cyan tint from the accent color
 */
@Composable
fun AnimatedNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val density = LocalDensity.current

    // Haptic on tab change
    var prevIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex != prevIndex) {
            HapticUtils.tick(view)
            prevIndex = selectedIndex
        }
    }

    // Spring-animated pill position
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "pillSlide",
    )

    // Measure the row
    var rowWidthPx by remember { mutableIntStateOf(0) }
    var rowHeightPx by remember { mutableIntStateOf(0) }

    // ── Container ───────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF0B0B14).copy(alpha = 0.88f),
                        Color(0xFF0B0B14),
                    ),
                )
            )
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = 10.dp)
            .navigationBarsPadding(),
    ) {
        val barShape = RoundedCornerShape(26.dp)

        // ── Bar background ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(barShape)
                .background(Color(0xFF131320).copy(alpha = 0.85f))
                .background(Color.White.copy(alpha = 0.03f))
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.02f),
                        ),
                    ),
                    shape = barShape,
                ),
        ) {
            // ── Liquid Glass Pill ────────────────────────────────────
            if (rowWidthPx > 0 && rowHeightPx > 0 && items.isNotEmpty()) {
                val itemWidthPx = rowWidthPx / items.size
                val pillOffsetPx = (animatedIndex * itemWidthPx).toInt()
                val pillWidthDp = with(density) { itemWidthPx.toDp() }
                val pillHeightDp = with(density) { rowHeightPx.toDp() }
                val pillShape = RoundedCornerShape(22.dp)

                // Specular highlight travels across pill as it slides
                // Normalized 0..1 across the full bar width
                val specularX = if (items.size > 1)
                    animatedIndex / (items.size - 1).toFloat()
                else 0.5f

                Box(
                    modifier = Modifier
                        .offset { IntOffset(pillOffsetPx, 0) }
                        .width(pillWidthDp)
                        .height(pillHeightDp)
                        .padding(horizontal = 5.dp, vertical = 4.dp)
                        .clip(pillShape)

                        // Layer 1: Base fill — very subtle warm white
                        .background(Color.White.copy(alpha = 0.08f))

                        // Layer 2: Gradient fill — brighter top, fades down
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.10f),
                                    Color.White.copy(alpha = 0.03f),
                                    Color.White.copy(alpha = 0.05f),
                                ),
                            )
                        )

                        // Layer 3: Travelling specular + inner shadow + rim
                        .drawBehind {
                            val w = size.width
                            val h = size.height
                            val cr = 22.dp.toPx()

                            // ── Specular highlight (moves with pill) ──
                            // An elliptical bright spot at the top that
                            // shifts left-right based on specularX
                            val spotCenterX = w * 0.2f + (w * 0.6f * specularX)
                            val spotRadiusX = w * 0.4f
                            val spotRadiusY = h * 0.45f
                            drawOval(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.14f),
                                        Color.White.copy(alpha = 0.04f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(spotCenterX, h * 0.15f),
                                    radius = spotRadiusX,
                                ),
                                topLeft = Offset(spotCenterX - spotRadiusX, -spotRadiusY * 0.3f),
                                size = Size(spotRadiusX * 2f, spotRadiusY * 2f),
                            )

                            // ── Subtle cyan tint at center ──
                            drawOval(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        CyanPrimary.copy(alpha = 0.05f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(w * 0.5f, h * 0.4f),
                                    radius = w * 0.5f,
                                ),
                                topLeft = Offset(0f, 0f),
                                size = Size(w, h),
                            )

                            // ── Inner bottom shadow (grounded depth) ──
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.12f),
                                    ),
                                    startY = h * 0.6f,
                                    endY = h,
                                ),
                                cornerRadius = CornerRadius(cr),
                            )

                            // ── Top edge bright line (rim light) ──
                            val rimPath = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        Rect(0f, 0f, w, h),
                                        CornerRadius(cr),
                                    )
                                )
                            }
                            drawPath(
                                path = rimPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.22f),
                                        Color.White.copy(alpha = 0.06f),
                                        Color.Transparent,
                                    ),
                                    startY = 0f,
                                    endY = h * 0.25f,
                                ),
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }

                        // Outer border — bright on top, near-invisible bottom
                        .border(
                            width = 0.75.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.25f),
                                    Color.White.copy(alpha = 0.08f),
                                    Color.White.copy(alpha = 0.03f),
                                ),
                            ),
                            shape = pillShape,
                        ),
                )
            }

            // ── Nav items ───────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        rowWidthPx = size.width
                        rowHeightPx = size.height
                    },
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selectedIndex

                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.06f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f, stiffness = 400f),
                        label = "ns$index",
                    )
                    val iconAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.35f,
                        animationSpec = tween(220),
                        label = "na$index",
                    )
                    val liftY by animateFloatAsState(
                        targetValue = if (isSelected) -1.5f else 0f,
                        animationSpec = spring(dampingRatio = 0.6f, stiffness = 350f),
                        label = "nl$index",
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onItemSelected(index) },
                            )
                            .padding(vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isSelected) CyanPrimary
                                   else Color.White.copy(alpha = iconAlpha),
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationY = liftY
                                },
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = item.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold
                                         else FontWeight.Normal,
                            color = if (isSelected) CyanPrimary
                                    else Color.White.copy(alpha = 0.38f),
                            modifier = Modifier.graphicsLayer {
                                translationY = liftY * 0.3f
                            },
                        )
                    }
                }
            }
        }
    }
}
