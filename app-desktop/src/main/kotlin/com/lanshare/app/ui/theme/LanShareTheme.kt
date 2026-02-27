package com.lanshare.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LanShareLightColorScheme = lightColorScheme(
    primary = Color(0xFF006B66),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9CF1EA),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A635F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCDE8E2),
    onSecondaryContainer = Color(0xFF051F1C),
    surface = Color(0xFFF4FBF9),
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDCE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

@Composable
fun LanShareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LanShareLightColorScheme,
        typography = Typography(),
        content = content
    )
}
