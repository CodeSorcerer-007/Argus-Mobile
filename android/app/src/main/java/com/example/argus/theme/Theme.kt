package com.example.argus.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val DarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = TextOnEmerald,
    primaryContainer = EmeraldDark,
    onPrimaryContainer = TextPrimary,
    secondary = CyanAccent,
    onSecondary = ObsidianBlack,
    secondaryContainer = ObsidianCard,
    onSecondaryContainer = TextPrimary,
    tertiary = CyanGlow,
    background = ObsidianBlack,
    onBackground = TextPrimary,
    surface = ObsidianSurface,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = ObsidianBorder,
    error = ShieldRed
)

@Composable
fun ArgusTheme(
    darkTheme: Boolean = true, // Default to stunning Obsidian Dark Mode
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            if (activity != null) {
                try {
                    val window = activity.window
                    @Suppress("DEPRECATION")
                    window.statusBarColor = ObsidianBlack.toArgb()
                    @Suppress("DEPRECATION")
                    window.navigationBarColor = ObsidianBlack.toArgb()
                    val insetsController = WindowCompat.getInsetsController(window, view)
                    insetsController.isAppearanceLightStatusBars = false
                    insetsController.isAppearanceLightNavigationBars = false
                } catch (e: Throwable) {
                    // Non-critical: Safe fallback on edge-to-edge Android 15/16
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
