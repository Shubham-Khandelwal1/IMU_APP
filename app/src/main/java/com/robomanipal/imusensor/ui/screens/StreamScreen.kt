package com.robomanipal.imusensor.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robomanipal.imusensor.streaming.StreamFormat
import com.robomanipal.imusensor.ui.components.GlassCard
import com.robomanipal.imusensor.ui.components.StatusIndicator
import com.robomanipal.imusensor.ui.theme.*
import com.robomanipal.imusensor.viewmodel.StreamViewModel

@Composable
fun StreamScreen(
    vm: StreamViewModel,
    modifier: Modifier = Modifier,
) {
    val config      by vm.config.collectAsStateWithLifecycle()
    val isStreaming  by vm.isStreaming.collectAsStateWithLifecycle()
    val packetsSent by vm.packetsSent.collectAsStateWithLifecycle()
    val errors      by vm.errors.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Stream",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )

        Spacer(Modifier.height(20.dp))

        // ── Connection Config ──────────────────────────────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = CyanPrimary,
        ) {
            // IP Address
            Text("Target IP", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            StyledTextField(
                value = config.targetIp,
                onValueChange = { vm.updateIp(it) },
                placeholder = "192.168.1.100",
                enabled = !isStreaming,
            )

            Spacer(Modifier.height(16.dp))

            // Port
            Text("Port", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            StyledTextField(
                value = config.port.toString(),
                onValueChange = { vm.updatePort(it.toIntOrNull() ?: 8765) },
                placeholder = "8765",
                keyboardType = KeyboardType.Number,
                enabled = !isStreaming,
            )

            Spacer(Modifier.height(16.dp))

            // Format selector
            Text("Format", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FormatChip("CSV",  config.format == StreamFormat.CSV,  !isStreaming) { vm.updateFormat(StreamFormat.CSV) }
                FormatChip("JSON", config.format == StreamFormat.JSON, !isStreaming) { vm.updateFormat(StreamFormat.JSON) }
            }

            Spacer(Modifier.height(16.dp))

            // Sample rate slider
            Text("Sample Rate: ${config.sampleRateHz} Hz", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(4.dp))
            Slider(
                value = config.sampleRateHz.toFloat(),
                onValueChange = { vm.updateSampleRate(it.toInt()) },
                valueRange = 10f..200f,
                steps = 18,
                enabled = !isStreaming,
                colors = SliderDefaults.colors(
                    thumbColor = CyanPrimary,
                    activeTrackColor = CyanPrimary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.1f),
                ),
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Start / Stop button ────────────────────────────────────────
        val buttonColor by animateColorAsState(
            targetValue = if (isStreaming) StatusError else CyanPrimary,
            animationSpec = spring(stiffness = 300f),
            label = "btnColor",
        )

        Button(
            onClick = { vm.toggleStreaming() },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
        ) {
            Text(
                text = if (isStreaming) "STOP STREAMING" else "START STREAMING",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = 1.sp,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Live stats ─────────────────────────────────────────────────
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            accentColor = if (isStreaming) StatusStreaming else Color.White,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusIndicator(
                    color = if (isStreaming) StatusStreaming else StatusInactive,
                    label = if (isStreaming) "Streaming" else "Idle",
                    animate = isStreaming,
                )
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatChip("Packets", packetsSent.toString())
                StatChip("Errors", errors.toString())
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

// ── Private helpers ────────────────────────────────────────────────────────

@Composable
private fun StyledTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(placeholder, color = TextTertiary)
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
            cursorColor = CyanPrimary,
            focusedBorderColor = CyanPrimary.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
            disabledBorderColor = Color.White.copy(alpha = 0.06f),
            disabledTextColor = TextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.04f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FormatChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) CyanPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f)
    val borderColor = if (selected) CyanPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f)

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) CyanPrimary else TextSecondary,
        )
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = TextTertiary)
        Text(
            text = value,
            fontFamily = SensorValueFont,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
        )
    }
}
