package br.com.vendamais.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Emerald,
    secondary = EmeraldDark,
    tertiary = Blue500,
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
)

@Composable
fun VendaMaisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content,
    )
}
