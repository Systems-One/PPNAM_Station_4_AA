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
    outline = GraphiteBorder
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
