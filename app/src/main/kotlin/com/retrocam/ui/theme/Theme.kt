package com.retrocam.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object RetroColors {
    val Cream = Color(0xFFF4E8D2)
    val Amber = Color(0xFFF2B544)
    val Orange = Color(0xFFE2683A)
    val Charcoal = Color(0xFF2B2733)
}

private val RetroDarkColorScheme = darkColorScheme(
    primary = RetroColors.Orange,        // the single accent colour
    onPrimary = RetroColors.Charcoal,
    secondary = RetroColors.Amber,
    onSecondary = RetroColors.Charcoal,
    background = RetroColors.Charcoal,
    onBackground = RetroColors.Cream,
    surface = RetroColors.Charcoal,
    onSurface = RetroColors.Cream,
)

/** Dark Material 3 theme; dynamic colour is deliberately off for a consistent retro look. */
@Composable
fun RetroCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RetroDarkColorScheme, content = content)
}
