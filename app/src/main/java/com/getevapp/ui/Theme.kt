package com.getevapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF6C28FE)
private val Colors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFE7FF),
    onPrimaryContainer = Color(0xFF39107A),
    secondary = Color(0xFF5E4776),
    background = Color(0xFFF8F9FA),
    onBackground = Color(0xFF21212B),
    surface = Color.White,
    onSurface = Color(0xFF21212B),
    surfaceVariant = Color(0xFFF0F1F5),
    onSurfaceVariant = Color(0xFF5E5E6D),
    outline = Color(0xFF797582),
    outlineVariant = Color(0xFFDDD9E4),
    error = Color(0xFFB3261E),
)

@Composable
fun GetEverythingTheme(content: @Composable () -> Unit) {
    // The RN app has a light theme; keep it consistent regardless of system theme.
    MaterialTheme(colorScheme = Colors, content = content)
}
