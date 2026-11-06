package com.mitas.ppnam.station4aa.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val AppColorScheme = darkColorScheme(
    primary = AmberPrimary,
    onPrimary = AmberDark,
    secondary = SuccessGreen,
    onSecondary = TextPrimary,
    background = GraphiteBackground,
    onBackground = TextPrimary,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = GraphiteSurfaceVariant,
    onSurfaceVariant = TextMuted,
    error = DangerRed,
    onError = TextPrimary,
    // Interactive outlines (OutlinedTextField, OutlinedButton, Switch) need 3:1; see GraphiteOutline.
    outline = GraphiteOutline,
    outlineVariant = GraphiteBorder,
    // darkColorScheme() leaves every surface container at the Material baseline (#211F26 and
    // friends, a warm purple-grey), which is what dropdown menus and the Switch's off track were
    // drawing in (visual critique 2026-11-06 P2-3). Map them onto the graphite palette instead.
    surfaceTint = GraphiteSurfaceVariant,
    surfaceDim = GraphiteBackground,
    surfaceBright = GraphiteSurfaceVariant,
    surfaceContainerLowest = GraphiteBackground,
    surfaceContainerLow = GraphiteSurface,
    surfaceContainer = GraphiteSurfaceVariant,
    surfaceContainerHigh = GraphiteSurfaceVariant,
    surfaceContainerHighest = GraphiteBorder,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

private val AppTextSelectionColors = TextSelectionColors(
    handleColor = BrandTint,
    backgroundColor = BrandTint.copy(alpha = 0.4f),
)

/** TextButton colours in the violet [BrandTint] foreground (the amber primary is reserved for fills). */
@Composable
fun brandTextButtonColors(): ButtonColors = ButtonDefaults.textButtonColors(contentColor = BrandTint)

/** OutlinedButton colours in [BrandTint]. */
@Composable
fun brandOutlinedButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = BrandTint)

@Composable
fun PPNAMStation4AATheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = {
            CompositionLocalProvider(LocalTextSelectionColors provides AppTextSelectionColors, content = content)
        }
    )
}
