package com.veruproject.vmusix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.veruproject.vmusix.brand.BrandConfig

/**
 * Tema Vmusix: warna aksen dipilih di Pengaturan, latar hitam AMOLED untuk mode gelap.
 */
private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = BrandConfig.SECONDARY_COLOR,
    onSecondary = Color.White,
    background = BrandConfig.BACKGROUND_COLOR,
    onBackground = BrandConfig.TEXT_COLOR,
    surface = BrandConfig.BACKGROUND_COLOR,
    onSurface = BrandConfig.TEXT_COLOR,
    surfaceVariant = BrandConfig.CARD_COLOR,
    onSurfaceVariant = Color(0xFFA1A1AA),
    surfaceContainer = BrandConfig.CARD_COLOR,
    surfaceContainerHigh = Color(0xFF1F1F23),
    tertiary = accent,
    outline = Color(0xFF3F3F46),
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondary = BrandConfig.SECONDARY_COLOR,
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF1F1F4),
    onSurfaceVariant = Color(0xFF52525B),
    surfaceContainer = Color(0xFFEDEDF0),
    surfaceContainerHigh = Color(0xFFE4E4E7),
    tertiary = accent,
    outline = Color(0xFFD4D4D8),
)

@Composable
fun VmusixTheme(
    accent: Color = BrandConfig.ACCENT_COLOR,
    darkMode: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkMode) darkScheme(accent) else lightScheme(accent),
        content = content,
    )
}
