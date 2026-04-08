package br.com.vendamais.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Emerald,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = EmeraldDark,
    secondary = BrandDarkGreen,
    secondaryContainer = EmeraldSoft,
    onSecondaryContainer = EmeraldDark,
    tertiary = Blue500,
    tertiaryContainer = Blue100,
    background = Slate50,
    surface = White,
    surfaceVariant = Slate100,
    onPrimary = White,
    onSecondary = White,
    onTertiary = White,
    onBackground = Slate900,
    onSurface = Slate800,
    onSurfaceVariant = Slate600,
    outline = Slate200,
    error = Red500,
    errorContainer = Red100,
    onErrorContainer = Red500,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF49C8A3),
    primaryContainer = Color(0xFF17372F),
    onPrimaryContainer = Color(0xFFB6F4DF),
    secondary = Color(0xFF7CD8B8),
    secondaryContainer = Color(0xFF1A3E34),
    onSecondaryContainer = Color(0xFFBDF5E3),
    tertiary = Color(0xFF86A8FF),
    tertiaryContainer = Color(0xFF1F2E57),
    onTertiaryContainer = Color(0xFFD8E1FF),
    background = Color(0xFF0D121B),
    surface = Color(0xFF131B27),
    surfaceVariant = Color(0xFF1B2635),
    onPrimary = Color(0xFF06261F),
    onSecondary = Color(0xFF0A2A23),
    onTertiary = Color(0xFF10214A),
    onBackground = Color(0xFFE6EDF7),
    onSurface = Color(0xFFE6EDF7),
    onSurfaceVariant = Color(0xFFB7C5D8),
    outline = Color(0xFF334256),
    error = Color(0xFFFF8A8A),
    errorContainer = Color(0xFF4A1D22),
    onErrorContainer = Color(0xFFFFD6D6),
)

@Composable
fun VendaMaisTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
