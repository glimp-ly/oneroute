package com.oneroute.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = PrimaryBlueDark,
    secondary = PrimaryBlueLight,
    secondaryContainer = SoftContainer,
    onSecondaryContainer = PrimaryBlueDark,
    background = CanvasBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = SoftContainer,
    onSurfaceVariant = TextSecondary,
    outline = OutlineSoft,
    error = DangerRed,
    errorContainer = DangerContainer
)

@Composable
fun OneRouteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightScheme, typography = Typography, content = content)
}
