package com.robomanipal.imusensor.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Background layers ──────────────────────────────────────────────
val DarkBackground     = Color(0xFF080810)
val DarkSurface        = Color(0xFF111118)
val DarkSurfaceVariant = Color(0xFF1A1A28)
val SurfaceElevated    = Color(0xFF111118)
val SurfaceElevated2   = Color(0xFF1A1A28)

// ── Glass effects ──────────────────────────────────────────────────
val GlassWhite         = Color(0x14FFFFFF)   // 8 %
val GlassBorder        = Color(0x1FFFFFFF)   // 12 %
val GlassHighlight     = Color(0x0DFFFFFF)   // 5 %

// ── Elevation / shadow tokens (no glow — soft ambient only) ───────
val CardShadowAmbient  = Color(0x40000000)   // 25 % black
val CardShadowSpot     = Color(0x26000000)   // 15 % black
val CardHighlightTop   = Color(0x12FFFFFF)   // 7 % white — inner top edge light
val CardHighlightBot   = Color(0x08000000)   // 3 % black — inner bottom edge weight

// ── Primary gradient endpoints ─────────────────────────────────────
val CyanPrimary        = Color(0xFF00D4FF)
val PurplePrimary      = Color(0xFF8B5CF6)
val MagentaPrimary     = Color(0xFFFF2DAA)

// ── Sensor-specific accent colors ──────────────────────────────────
val AccelColor         = Color(0xFF00D4FF)
val AccelColorDim      = Color(0x4D00D4FF)
val GyroColor          = Color(0xFF8B5CF6)
val GyroColorDim       = Color(0x4D8B5CF6)
val MagColor           = Color(0xFFF59E0B)
val MagColorDim        = Color(0x4DF59E0B)
val OrientColor        = Color(0xFF10B981)
val OrientColorDim     = Color(0x4D10B981)

// ── Flow / animated element colors ─────────────────────────────────
val FlowLineColor      = Color(0xFF38BDF8)
val StreamActiveColor  = Color(0xFF22D3EE)
val FusionNodeColor    = Color(0xFF6366F1)

// ── Axis colors (consistent across all charts) ─────────────────────
val AxisXColor         = Color(0xFFFF5252)
val AxisYColor         = Color(0xFF69F0AE)
val AxisZColor         = Color(0xFF448AFF)

// ── Status indicators ──────────────────────────────────────────────
val StatusActive       = Color(0xFF10B981)
val StatusStreaming     = Color(0xFF22D3EE)
val StatusInactive     = Color(0xFF757575)
val StatusError        = Color(0xFFEF4444)
val DangerColor        = Color(0xFFEF4444)

// ── Text hierarchy ─────────────────────────────────────────────────
val TextPrimary        = Color(0xF2FFFFFF)   // 95 %
val TextSecondary      = Color(0x99FFFFFF)   // 60 %
val TextTertiary       = Color(0x4DFFFFFF)   // 30 %

// ── Gradient presets ───────────────────────────────────────────────
val AccelGradient  = Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF0EA5E9)))
val GyroGradient   = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFA78BFA)))
val MagGradient    = Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFFBBF24)))
val OrientGradient = Brush.linearGradient(listOf(Color(0xFF10B981), Color(0xFF34D399)))
val FusionGradient = Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF818CF8)))

// ── Horizon colors ─────────────────────────────────────────────────
val HorizonSkyTop      = Color(0xFF0C1445)
val HorizonSkyBottom   = Color(0xFF1E3A7A)
val HorizonGroundTop   = Color(0xFF4A2F14)
val HorizonGroundBottom= Color(0xFF2A1A0A)
val HorizonLineColor   = Color(0xFFFFFFFF)
