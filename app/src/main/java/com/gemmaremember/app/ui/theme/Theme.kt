package com.gemmaremember.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A6DD4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4EFFE),
    onPrimaryContainer = Color(0xFF0D3268),
    secondary = Color(0xFF5088C3),
    background = Color(0xFFF0F5FF),
    surface = Color.White,
    onBackground = Color(0xFF0D3268),
    onSurface = Color(0xFF1A2940),
    surfaceVariant = Color(0xFFE4EAF2),
    outline = Color(0xFFE4EAF2)
)

@Composable
fun GemmaRememberTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography(),
        content = content
    )
}
