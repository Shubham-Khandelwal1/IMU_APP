package com.robomanipal.imusensor.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robomanipal.imusensor.streaming.StreamFormat
import com.robomanipal.imusensor.ui.components.GlassCard
import com.robomanipal.imusensor.ui.components.HapticUtils
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

    val view = LocalView.current

    // ── Entrance animation ─────────────────────────────────────────
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60);  step = 1
        kotlinx.coroutines.delay(100); step = 2
        kotlinx.coroutines.delay(90);  step = 3
    }

    val sa1 by animateFloatAsState(if (step >= 1) 1f else 0f, tween(400), label = "sa1")
    val so1 by animateFloatAsState(if (step >= 1) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so1")
    val sa2 by animateFloatAsState(if (step >= 2) 1f else 0f, tween(400), label = "sa2")
    val so2 by animateFloatAsState(if (step >= 2) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so2")
    val sa3 by animateFloatAsState(if (step >= 3) 1f else 0f, tween(400), label = "sa3")
    val so3 by animateFloatAsState(if (step >= 3) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so3")

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
            modifier = Modifier.graphicsLayer {
                alpha = sa1; translationY = so1
            },
        )

        Spacer(Modifier.height(20.dp))

        // ── Connection Config ──────────────────────────────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa1; translationY = so1 }) {
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
                    FormatChip("CSV",  config.format == StreamFormat.CSV,  !isStreaming) {
                        HapticUtils.tick(view)
                        vm.updateFormat(StreamFormat.CSV)
                    }
                    FormatChip("JSON", config.format == StreamFormat.JSON, !isStreaming) {
                        HapticUtils.tick(view)
                        vm.updateFormat(StreamFormat.JSON)
                    }
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
        }

        Spacer(Modifier.height(24.dp))

        // ── Start / Stop button with bounce animation ──────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa2; translationY = so2 }) {
            val buttonColor by animateColorAsState(
                targetValue = if (isStreaming) StatusError else CyanPrimary,
                animationSpec = spring(stiffness = 300f),
                label = "btnColor",
            )
            val buttonInteraction = remember { MutableInteractionSource() }
            val btnPressed by buttonInteraction.collectIsPressedAsState()
            val btnScale by animateFloatAsState(
                targetValue = if (btnPressed) 0.95f else 1f,
                animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
                label = "btnScale",
            )

            Button(
                onClick = {
                    HapticUtils.heavy(view)
                    vm.toggleStreaming()
                },
                interactionSource = buttonInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer { scaleX = btnScale; scaleY = btnScale },
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
        }

        Spacer(Modifier.height(20.dp))

        // ── Live stats ─────────────────────────────────────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa3; translationY = so3 }) {
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
    val bg by animateColorAsState(
        if (selected) CyanPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
        label = "chipBg",
    )
    val borderColor by animateColorAsState(
        if (selected) CyanPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f),
        label = "chipBorder",
    )

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
