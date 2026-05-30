package com.rokidbrew.phone

import androidx.compose.foundation.background
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

internal const val NEW_CATEGORY = "New"

@Composable
internal fun RokidBrewTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BrewBg,
            surface = BrewPanel,
            primary = BrewGreen,
            onPrimary = BrewBg,
            onSurface = BrewText,
        ),
        content = {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = density.density,
                    fontScale = density.fontScale.coerceAtMost(1.0f),
                ),
            ) {
                Surface(color = BrewBg, content = content)
            }
        },
    )
}
internal val BrewFont = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)
internal val BrewBg = Color(0xFF020404)
internal val BrewPanel = Color(0xFF0B0F0F)
internal val BrewPanelAlt = Color(0xFF111616)
internal val BrewPanelHi = Color(0xFF1A2020)
internal val BrewTextBright = Color(0xFFF1F6F4)
internal val BrewText = Color(0xFFCDD6D2)
internal val BrewMuted = Color(0xFF98A19D)
internal val BrewDim = Color(0xFF5D6A65)
internal val BrewGreen = Color(0xFF8CFF2F)
internal val BrewGreenDim = Color(0xFF4BAA28)
internal val BrewCyan = Color(0xFF56C8F2)
internal val BrewBorder = Color(0xFF1E2524)
internal val BrewBorderHi = Color(0xFF303938)
internal val BrewAmber = Color(0xFFFFB347)
internal val BrewRed = Color(0xFFFF5A5F)
