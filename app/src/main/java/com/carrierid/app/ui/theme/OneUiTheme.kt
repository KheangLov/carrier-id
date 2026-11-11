package com.carrierid.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours modelled on Samsung One UI: soft grey page, white rounded cards, blue accent, true-black dark mode. */
@Immutable
data class OneColors(
    val background: Color,
    val card: Color,
    val text: Color,
    val textSecondary: Color,
    val accent: Color,
    val divider: Color,
    val field: Color,
    val dark: Boolean,
)

private val Light = OneColors(
    background = Color(0xFFF4F4F4),
    card = Color(0xFFFFFFFF),
    text = Color(0xFF111111),
    textSecondary = Color(0xFF7D7D7D),
    accent = Color(0xFF3E7BFA),
    divider = Color(0xFFE8E8E8),
    field = Color(0xFFEDEDED),
    dark = false,
)

private val Dark = OneColors(
    background = Color(0xFF000000),
    card = Color(0xFF171717),
    text = Color(0xFFF5F5F5),
    textSecondary = Color(0xFF9A9A9A),
    accent = Color(0xFF6A9BFF),
    divider = Color(0xFF2A2A2A),
    field = Color(0xFF262626),
    dark = true,
)

val LocalOne = staticCompositionLocalOf { Light }

object One {
    val colors: OneColors @Composable get() = LocalOne.current
}

@Composable
fun OneUiTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) Dark else Light
    val scheme = if (c.dark) {
        darkColorScheme(primary = c.accent, background = c.background, surface = c.card,
            onSurface = c.text, onBackground = c.text)
    } else {
        lightColorScheme(primary = c.accent, background = c.background, surface = c.card,
            onSurface = c.text, onBackground = c.text)
    }
    CompositionLocalProvider(LocalOne provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
