package com.resq.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ResQColorScheme = darkColorScheme(
    primary = SignalRed,
    onPrimary = TextPrimary,
    secondary = BeaconGreen,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = SurfaceElevated,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = Divider,
    error = SignalRed,
)

/**
 * Always dark, regardless of system theme — see [OledBlack]'s doc for
 * why. [isSystemInDarkTheme] is intentionally unused; this is not a
 * light/dark toggle, it's a single battery-first theme.
 */
@Composable
fun ResQTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ResQColorScheme,
        typography = ResQTypography,
        content = content,
    )
}
