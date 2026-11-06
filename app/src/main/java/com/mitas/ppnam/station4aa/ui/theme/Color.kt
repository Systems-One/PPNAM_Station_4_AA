package com.mitas.ppnam.station4aa.ui.theme

import androidx.compose.ui.graphics.Color

// Background layers — matches WPF WindowBackgroundColor / AppBackgroundColor / PanelBackgroundColor
val GraphiteBackground     = Color(0xFF07101A)
val GraphiteSurface        = Color(0xFF102233)
val GraphiteSurfaceVariant = Color(0xFF14293D)
val GraphiteBorder         = Color(0xFF25384C)
// Outline for interactive components (field borders, outlined buttons, switch off-state).
// GraphiteBorder is only 1.35:1 on GraphiteSurface and 1.59:1 on GraphiteBackground, so every
// unfocused field read as borderless (visual critique 2026-11-06 P1-1). GraphiteBorder stays for
// card borders and dividers. The value is the suite's `field_stroke` (consistency rule C1, Station
// 1's text_field_stroke.xml): 4.85:1 on GraphiteSurface, 5.74:1 on GraphiteBackground (skill
// critique 2026-11-06 P1-1; the earlier #5E7690 passed 3:1 but drifted from the suite).
val GraphiteOutline        = Color(0xFF7A8FA5)

// Text — matches WPF TextColor / SecondaryTextColor
val TextPrimary            = Color(0xFFEDF4FB)
val TextMuted              = Color(0xFF9BAEC0)

// Primary accent — the launcher icon's violet (res/values/ic_launcher_background.xml), so the
// app carries its icon identity on screen the way Stations 1/3/5 do (audit static-02).
val BrandPrimary           = Color(0xFF55368C)
val AmberPrimary           = BrandPrimary
val AmberDark              = Color(0xFFFFFFFF)   // on-primary (white text on violet buttons)
// Lighter violet for foreground uses (text, icon tint, focused outline/label, cursor, spinners) on the
// graphite surfaces, where the filled-surface violet is below 3:1. It is the icon pack's own
// light tint for Station 4 (the back "P", `Android app logo directions/README.md`), which suite
// rule C1 names as the focused field outline (skill critique 2026-11-06 P1-1; was #B39DDB).
// 7.62:1 on GraphiteSurface, 9.01:1 on GraphiteBackground.
val BrandTint              = Color(0xFFBCAAD9)

// Status colours — matches WPF GreenColor / RedColor
val SuccessGreen           = Color(0xFF2BC36D)
val DangerRed              = Color(0xFFE25C5C)

// Secondary accents — matches WPF OrangeColor / CyanColor / PurpleColor
val InfoBlue               = Color(0xFF2E77F5)
val IndigoAccent           = Color(0xFF8A63E8)
val WarningOrange          = Color(0xFFF0A13A)
val CyanAccent             = Color(0xFF25C7DA)
