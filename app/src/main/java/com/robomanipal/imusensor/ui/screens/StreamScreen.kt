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
import androidx.compose.ui.graphics.Brush
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

    // ── Advanced config collapsed state ─────────────────────────────
    var showAdvanced by remember { mutableStateOf(false) }

    // ── Entrance animation ─────────────────────────────────────────
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(60);  step = 1
        kotlinx.coroutines.delay(100); step = 2
        kotlinx.coroutines.delay(90);  step = 3
        kotlinx.coroutines.delay(80);  step = 4
    }

    val sa1 by animateFloatAsState(if (step >= 1) 1f else 0f, tween(400), label = "sa1")
    val so1 by animateFloatAsState(if (step >= 1) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so1")
    val sa2 by animateFloatAsState(if (step >= 2) 1f else 0f, tween(400), label = "sa2")
    val so2 by animateFloatAsState(if (step >= 2) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so2")
    val sa3 by animateFloatAsState(if (step >= 3) 1f else 0f, tween(400), label = "sa3")
    val so3 by animateFloatAsState(if (step >= 3) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so3")
    val sa4 by animateFloatAsState(if (step >= 4) 1f else 0f, tween(400), label = "sa4")
    val so4 by animateFloatAsState(if (step >= 4) 0f else 40f, spring(dampingRatio = 0.65f, stiffness = 180f), label = "so4")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A16),
                        DarkBackground,
                        DarkBackground,
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .statusBarsPadding(),
    ) {
        Spacer(Modifier.height(12.dp))

        // ── Header ─────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = sa1; translationY = so1 },
        ) {
            Text(
                text = "Stream",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            // Status badge
            val badgeColor by animateColorAsState(
                if (isStreaming) StreamActiveColor else StatusInactive,
                tween(300), label = "badgeColor",
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (isStreaming) "LIVE" else "IDLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor,
                    letterSpacing = 1.5.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Stream Stats ─────────────────────────────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa2; translationY = so2 }) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                accentColor = if (isStreaming) StreamActiveColor else Color.White,
                accentGlow = isStreaming,
            ) {
                // 2×2 stats grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StreamStatBox("Packets", packetsSent.toString(), StreamActiveColor)
                    StreamStatBox("Errors", errors.toString(), if (errors > 0) DangerColor else TextTertiary)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StreamStatBox("Rate", "${config.sampleRateHz} Hz", CyanPrimary)
                    StreamStatBox("Format", config.format.name, MagColor)
                }

                Spacer(Modifier.height(14.dp))

                // Target display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.03f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Target",
                            fontSize = 10.sp,
                            color = TextTertiary,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${config.targetIp}:${config.port}",
                            fontFamily = SensorValueFont,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Quick Presets ──────────────────────────────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa3; translationY = so3 }) {
            Column {
                Text(
                    text = "PRESETS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextTertiary,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PresetChip(
                        label = "Lab",
                        detail = "50Hz CSV",
                        color = OrientColor,
                        enabled = !isStreaming,
                        onClick = {
                            HapticUtils.tick(view)
                            vm.updateSampleRate(50)
                            vm.updateFormat(StreamFormat.CSV)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PresetChip(
                        label = "Fast",
                        detail = "200Hz CSV",
                        color = CyanPrimary,
                        enabled = !isStreaming,
                        onClick = {
                            HapticUtils.tick(view)
                            vm.updateSampleRate(200)
                            vm.updateFormat(StreamFormat.CSV)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    PresetChip(
                        label = "Debug",
                        detail = "25Hz JSON",
                        color = GyroColor,
                        enabled = !isStreaming,
                        onClick = {
                            HapticUtils.tick(view)
                            vm.updateSampleRate(25)
                            vm.updateFormat(StreamFormat.JSON)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Start / Stop button ────────────────────────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa3; translationY = so3 }) {
            val buttonColor by animateColorAsState(
                targetValue = if (isStreaming) DangerColor else CyanPrimary,
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
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            ) {
                Text(
                    text = if (isStreaming) "STOP STREAMING" else "START STREAMING",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    letterSpacing = 1.5.sp,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Advanced Configuration (collapsible) ───────────────────
        Box(modifier = Modifier.graphicsLayer { alpha = sa4; translationY = so4 }) {
            Column {
                // Tap to expand
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            HapticUtils.tick(view)
                            showAdvanced = !showAdvanced
                        }
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        text = "ADVANCED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextTertiary,
                        letterSpacing = 2.sp,
                        modifier = Modifier.weight(1f),
                    )
                    val rotateIcon by animateFloatAsState(
                        if (showAdvanced) 180f else 0f,
                        spring(dampingRatio = 0.6f, stiffness = 200f),
                        label = "advRotate",
                    )
                    Text(
                        text = "▾",
                        fontSize = 14.sp,
                        color = TextTertiary,
                        modifier = Modifier.graphicsLayer { rotationZ = rotateIcon },
                    )
                }

                androidx.compose.animation.AnimatedVisibility(visible = showAdvanced) {
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

                        Spacer(Modifier.height(14.dp))

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

                        Spacer(Modifier.height(14.dp))

                        // Format selector
                        Text("Format", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FormatChip("CSV", config.format == StreamFormat.CSV, !isStreaming) {
                                HapticUtils.tick(view)
                                vm.updateFormat(StreamFormat.CSV)
                            }
                            FormatChip("JSON", config.format == StreamFormat.JSON, !isStreaming) {
                                HapticUtils.tick(view)
                                vm.updateFormat(StreamFormat.JSON)
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Sample rate slider
                        Text(
                            "Sample Rate: ${config.sampleRateHz} Hz",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                        )
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
                                inactiveTrackColor = Color.White.copy(alpha = 0.08f),
                            ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(100.dp))
    }
}

// ── Private helpers ────────────────────────────────────────────────────────

@Composable
private fun StreamStatBox(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(120.dp),
    ) {
        Text(
            text = value,
            fontFamily = SensorValueFont,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = TextTertiary,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
private fun PresetChip(
    label: String,
    detail: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha = if (enabled) 1f else 0.4f
    GlassCard(
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        accentColor = color,
        cornerRadius = 16.dp,
        onClick = if (enabled) onClick else null,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = detail,
            fontSize = 9.sp,
            color = TextTertiary,
        )
    }
}

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
            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
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
        if (selected) CyanPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
        label = "chipBorder",
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(0.5.dp, borderColor, RoundedCornerShape(12.dp))
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
