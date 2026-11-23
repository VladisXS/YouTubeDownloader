package com.vladisx.youtubedownloader.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/* ---------------- Cosmic palette ---------------- */

/** Nebula violet — the main "ship" accent. */
val Nebula = Color(0xFF9B6BFF)
val NebulaDeep = Color(0xFF6D28D9)
/** Ion cyan — secondary accent, star glow. */
val Ion = Color(0xFF3EE8FF)
/** Solar pink — highlights and energy. */
val Solar = Color(0xFFF472B6)
/** Dark-matter background layers. */
val Void = Color(0xFF04030C)
val VoidElevated = Color(0xFF0A0B1E)
val VoidCard = Color(0xFF11142C)

/**
 * The app is space-only: it always renders the dark nebula palette,
 * regardless of the system light/dark setting.
 */
val CosmicColors = darkColorScheme(
    primary = Nebula,
    onPrimary = Color.White,
    primaryContainer = NebulaDeep,
    onPrimaryContainer = Color.White,
    secondary = Ion,
    onSecondary = Color(0xFF021418),
    tertiary = Solar,
    onTertiary = Color(0xFF1A0413),
    background = Void,
    onBackground = Color(0xFFF0EEFF),
    surface = VoidElevated,
    onSurface = Color(0xFFF0EEFF),
    surfaceVariant = VoidCard,
    onSurfaceVariant = Color(0xFFB0ADCE),
    outline = Color(0xFF4A4E78),
    outlineVariant = Color(0xFF2B2F4E),
    error = Color(0xFFFF7A96),
    onError = Color(0xFF2A0410),
)

@Composable
fun YtDlTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            // Always light icons: the background is deep space.
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(colorScheme = CosmicColors, content = content)
}
