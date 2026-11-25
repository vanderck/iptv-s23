package com.vanderck.xtreamtv.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The palette of the macOS app: near-black backdrop, translucent white platters. */
object TvStyle {
    val background = Color(0xFF0B0B0F)
    val platter = Color(0xFF1C1C22)
    val platterHigh = Color(0xFF2A2A32)
    val secondaryText = Color(0xFFA0A0AA)
    val tertiaryText = Color(0xFF6E6E78)
    val accent = Color(0xFF6E7BFF)
    val favorite = Color(0xFFFFD60A)
    val live = Color(0xFFFF453A)
    val cornerRadius = 12.dp
}

@Composable
fun XtreamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color.White,
            onPrimary = Color.Black,
            secondary = TvStyle.accent,
            background = TvStyle.background,
            onBackground = Color.White,
            surface = TvStyle.background,
            onSurface = Color.White,
            surfaceVariant = TvStyle.platter,
            onSurfaceVariant = TvStyle.secondaryText,
            surfaceContainer = TvStyle.platter,
            surfaceContainerHigh = TvStyle.platterHigh,
            surfaceContainerHighest = TvStyle.platterHigh,
            outline = TvStyle.tertiaryText,
        ),
    ) {
        // Material3 text defaults to black outside a Surface; this app is always dark.
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}
