package com.jd.softphone.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF0B6E4F)
private val GreenDark = Color(0xFF09583F)
private val Accent = Color(0xFF2DD4A7)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = Accent,
    primaryContainer = Color(0xFFB8F2DE),
    onPrimaryContainer = GreenDark,
)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00382A),
    secondary = Green,
    primaryContainer = GreenDark,
    onPrimaryContainer = Color.White,
)

@Composable
fun SoftphoneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
