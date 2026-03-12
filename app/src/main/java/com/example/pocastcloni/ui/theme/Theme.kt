package com.example.pocastcloni.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.example.pocastcloni.ui.settings.AppColor
import com.example.pocastcloni.ui.settings.AppTheme
import kotlin.math.max

// --- COLOR MATH HELPERS ---

private fun Color.darken(factor: Float): Color {
    val darkenedFactor = max(0f, 1f - factor)
    return Color(
        red = this.red * darkenedFactor,
        green = this.green * darkenedFactor,
        blue = this.blue * darkenedFactor,
        alpha = this.alpha
    )
}

private fun Color.lighten(factor: Float): Color {
    val white = Color.White.toArgb()
    val colored = this.toArgb()
    val blended = ColorUtils.blendARGB(colored, white, factor)
    return Color(blended)
}

private fun Color.contrastColor(): Color {
    return if (this.luminance() > 0.5f) Color.Black else Color.White
}

// --- THEME ---

@Composable
fun PocastCloniTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    appColor: AppColor = AppColor.GREEN,
    colorStrength: Float = 0.1f, // Default to a subtle tint
    content: @Composable () -> Unit
) {
    val darkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    val seedColor = Color(appColor.hexValue)
    val onSeedColor = seedColor.contrastColor()

    val colorScheme = if (darkTheme) {
        val backgroundFactor = 1f - (colorStrength * 0.9f) // Range from 1.0 (black) to 0.1 (strong color)
        val surfaceFactor = 1f - (colorStrength * 0.85f)   // Slightly lighter than background

        val darkBackground = seedColor.darken(backgroundFactor)
        val onDarkBackground = darkBackground.contrastColor()
        val darkSurface = seedColor.darken(surfaceFactor)
        val onDarkSurface = darkSurface.contrastColor()

        darkColorScheme(
            primary = seedColor,
            onPrimary = onSeedColor,
            secondary = seedColor.lighten(0.2f),
            onSecondary = seedColor.lighten(0.2f).contrastColor(),
            tertiary = seedColor.darken(0.8f),
            background = darkBackground,
            onBackground = onDarkBackground,
            surface = darkSurface,
            onSurface = onDarkSurface
        )
    } else {
        val backgroundFactor = (1f - colorStrength) * 0.15f + 0.85f // Range from 1.0 (white) to 0.85 (strong color)

        val lightBackground = seedColor.lighten(backgroundFactor)
        val onLightBackground = lightBackground.contrastColor()

        lightColorScheme(
            primary = seedColor,
            onPrimary = onSeedColor,
            secondary = seedColor.darken(0.8f),
            onSecondary = seedColor.darken(0.8f).contrastColor(),
            tertiary = seedColor.lighten(0.4f),
            background = lightBackground,
            onBackground = onLightBackground,
            surface = lightBackground, // Surface is same as background in light theme
            onSurface = onLightBackground
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findActivity()
            activity?.window?.let { window ->
                window.statusBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
