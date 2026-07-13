package com.light.medication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BlackAndWhiteColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = Black,
    onPrimaryContainer = White,
    secondary = White,
    onSecondary = Black,
    secondaryContainer = Black,
    onSecondaryContainer = White,
    tertiary = White,
    onTertiary = Black,
    tertiaryContainer = Black,
    onTertiaryContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Black,
    onSurfaceVariant = White,
    error = White,
    onError = Black,
    errorContainer = Black,
    onErrorContainer = White,
    outline = White,
    outlineVariant = Gray,
    scrim = Black
)

@Composable
fun LIghtMedicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BlackAndWhiteColorScheme,
        typography = Typography,
        content = content
    )
}
