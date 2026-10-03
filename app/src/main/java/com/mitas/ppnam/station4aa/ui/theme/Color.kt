package com.mitas.ppnam.station4aa.ui.theme

import androidx.compose.ui.graphics.Color

// Background layers — matches WPF WindowBackgroundColor / AppBackgroundColor / PanelBackgroundColor
val GraphiteBackground     = Color(0xFF07101A)
val GraphiteSurface        = Color(0xFF102233)
val GraphiteSurfaceVariant = Color(0xFF14293D)
val GraphiteBorder         = Color(0xFF25384C)

// Text — matches WPF TextColor / SecondaryTextColor
val TextPrimary            = Color(0xFFEDF4FB)
val TextMuted              = Color(0xFF9BAEC0)

// Primary accent — the launcher icon's violet (res/values/ic_launcher_background.xml), so the
// app carries its icon identity on screen the way Stations 1/3/5 do (audit static-02).
val BrandPrimary           = Color(0xFF55368C)
val AmberPrimary           = BrandPrimary
val AmberDark              = Color(0xFFFFFFFF)   // on-primary (white text on violet buttons)
// Lighter violet for foreground uses (text, icon tint, focused outline/label, cursor, spinners) on the
// graphite surfaces, where the filled-surface violet is below 3:1.
val BrandTint              = Color(0xFFB39DDB)

// Status colours — matches WPF GreenColor / RedColor
val SuccessGreen           = Color(0xFF2BC36D)
val DangerRed              = Color(0xFFE25C5C)

// Secondary accents — matches WPF OrangeColor / CyanColor / PurpleColor
val InfoBlue               = Color(0xFF2E77F5)
val IndigoAccent           = Color(0xFF8A63E8)
val WarningOrange          = Color(0xFFF0A13A)
val CyanAccent             = Color(0xFF25C7DA)
