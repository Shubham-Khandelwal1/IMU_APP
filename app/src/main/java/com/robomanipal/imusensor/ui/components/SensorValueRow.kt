package com.robomanipal.imusensor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.robomanipal.imusensor.ui.theme.SensorValueFont
import com.robomanipal.imusensor.ui.theme.TextSecondary

/**
 * A single row showing an axis label, live animated numeric value, and unit.
 * The value smoothly animates between readings so digits don't visually jump.
 */
@Composable
fun SensorValueRow(
    label: String,
    value: Float,
    unit: String = "",
    color: Color,
    modifier: Modifier = Modifier,
    decimals: Int = 3,
) {
    val animated by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "sensorVal",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Axis label with colored dot
        Box(
            modifier = Modifier.width(32.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Animated numeric value
        Text(
            text = "%+.${decimals}f".format(animated),
            fontFamily = SensorValueFont,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.weight(1f),
        )

        // Unit
        if (unit.isNotEmpty()) {
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
        }
    }
}
