package com.eats.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Note: `tertiary` is used for link text (bold orange in light mode, light purple in dark mode)
// because the primary colour is too low-contrast for small text on the page background.

private val LightColors = lightColorScheme(
    primary = Orange600,
    onPrimary = Color.White,
    primaryContainer = Orange100,
    onPrimaryContainer = Orange900,
    secondary = Ink,
    onSecondary = Color.White,
    tertiary = Orange700,
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF5F5F4),
    onSurfaceVariant = Color(0xFF57534E),
    outline = Color(0xFF9A948E),
    outlineVariant = Color(0xFFE7E5E4),
    error = Color(0xFFB91C1C),
    onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Purple600,
    onPrimary = Color.White,
    primaryContainer = Purple950,
    onPrimaryContainer = Purple100,
    secondary = Purple400,
    onSecondary = Night,
    tertiary = Purple400,
    onTertiary = Night,
    background = Night,
    onBackground = Color(0xFFF5F5F5),
    surface = Night,
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF666666),
    outlineVariant = Color(0xFF262626),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
)

private val EatsShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)

/** Follows the phone's light/dark setting. Pass [darkTheme] to force one for testing. */
@Composable
fun EatsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = EatsTypography,
        shapes = EatsShapes,
        content = content,
    )
}
