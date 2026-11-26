package com.animein.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Musike / a-music Inspired Cool Graphite Surface Hierarchy ──────────────
val BgGraphite = Color(0xFF0B0C0F)         // --bg: deep cool graphite canvas
val Surface1 = Color(0xFF121318)           // --surface-1: island outer shell / primary panel
val Surface2 = Color(0xFF181A20)           // --surface-2: island core / cards / chips
val Surface3 = Color(0xFF20232B)           // --surface-3: elevated controls / active neutral
val Surface4 = Color(0xFF2A2E38)           // --surface-4: hover / pressed / strong neutral

val Hairline = Color(0x12FFFFFF)           // --line: 7% white hairline border
val HairlineStrong = Color(0x1FFFFFFF)     // --line-strong: 12% white border
val HighlightTop = Color(0x0FFFFFFF)       // --highlight: 6% subtle top edge sheen
val ScrimDark = Color(0xB8050608)          // --scrim: modal / overlay scrim
val DangerCoral = Color(0xFFFF7A6B)        // --danger: soft coral error / destructive

val FgPrimary = Color(0xFFECEEF2)          // --fg: crisp primary text
val FgSecondary = Color(0xFF9BA1AD)        // --fg-2: muted secondary text
val FgTertiary = Color(0xFF6B717D)         // --fg-3: subtle metadata / inactive icons

// ── Customizable Accent Presets (8 presets matching a-music) ───────────────
data class AccentPreset(
    val id: String,
    val label: String,
    val accent: Color,
    val onAccent: Color = Color(0xFF0B0C0F),
    val accentSoft: Color = accent.copy(alpha = 0.14f),
    val accentRingSoft: Color = accent.copy(alpha = 0.30f)
)

val AccentPresets: List<AccentPreset> = listOf(
    AccentPreset(id = "volt", label = "Volt", accent = Color(0xFFC3EC52)),
    AccentPreset(id = "mint", label = "Mint", accent = Color(0xFF34D399)),
    AccentPreset(id = "aqua", label = "Aqua", accent = Color(0xFF22D3EE)),
    AccentPreset(id = "sky", label = "Sky", accent = Color(0xFF60A5FA)),
    AccentPreset(id = "violet", label = "Violet", accent = Color(0xFFA78BFA)),
    AccentPreset(id = "pink", label = "Pink", accent = Color(0xFFF472B6)),
    AccentPreset(id = "coral", label = "Coral", accent = Color(0xFFFB7C5C)),
    AccentPreset(id = "amber", label = "Amber", accent = Color(0xFFFBBF24))
)

val DefaultAccentPreset: AccentPreset = AccentPresets.first()

fun accentPresetById(id: String): AccentPreset =
    AccentPresets.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DefaultAccentPreset

// ── Backward-compatible aliases (mapped to Cool Graphite tokens) ───────────
val DarkBackground = BgGraphite
val DarkSurface = Surface1
val DarkSurfaceVariant = Surface2

val FrostGlassBgTop = Surface3
val FrostGlassBgMid = Surface2
val FrostGlassBgBottom = Surface1
val OutlineGreyHighlight = Color(0x38FFFFFF)
val OutlineGrey = Color(0x28FFFFFF)
val OutlineGreySubtle = HairlineStrong
val FrostWhiteAccent = FgPrimary
val FrostActiveBg = Color(0x1EFFFFFF)

val Purple80 = Color(0xFFE0E0E5)
val PurpleGrey80 = Color(0xFFC8C8D0)
val Pink80 = Color(0xFFD8D8E0)

val Purple40 = Color(0xFF5A5A64)
val PurpleGrey40 = Color(0xFF4E4E57)
val Pink40 = Color(0xFF64646E)

val NeonPurple = Color(0xFFC3EC52)
val NeonPink = Color(0xFFD4D4DC)
val NeonCyan = Color(0xFFC7C7CC)

val TextPrimary = FgPrimary
val TextSecondary = FgSecondary
val StarYellow = Color(0xFFFBBF24)
