package com.example.pocastcloni.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.model.GradientDirection
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
    return bestContrastingColor(this)
}

fun gradientBackgroundBottomColor(
    appColor: AppColor,
    darkTheme: Boolean,
    strength: Float
): Color {
    val topColor = if (darkTheme) Color.Black else Color.White
    return Color(
        ColorUtils.blendARGB(
            topColor.toArgb(),
            Color(appColor.hexValue).toArgb(),
            strength.coerceIn(0f, 1f)
        )
    )
}

fun gradientBackgroundColors(
    appColor: AppColor,
    darkTheme: Boolean,
    strength: Float
): List<Color> =
    listOf(
        if (darkTheme) Color.Black else Color.White,
        gradientBackgroundBottomColor(
            appColor = appColor,
            darkTheme = darkTheme,
            strength = strength
        )
    )

fun gradientBackgroundBrush(
    appColor: AppColor,
    darkTheme: Boolean,
    strength: Float,
    direction: GradientDirection,
    rootSize: Size,
    offsetInRoot: Offset = Offset.Zero
): Brush {
    val safeSize =
        Size(
            width = rootSize.width.coerceAtLeast(1f),
            height = rootSize.height.coerceAtLeast(1f)
        )
    val (start, end) = gradientDirectionOffsets(direction, safeSize)
    return Brush.linearGradient(
        colors = gradientBackgroundColors(appColor, darkTheme, strength),
        start = start - offsetInRoot,
        end = end - offsetInRoot
    )
}

fun gradientBackgroundSystemBarColor(
    appColor: AppColor,
    darkTheme: Boolean,
    strength: Float,
    direction: GradientDirection,
    topEdge: Boolean
): Color {
    val colors = gradientBackgroundColors(appColor, darkTheme, strength)
    val fraction =
        when (direction) {
            GradientDirection.TOP_TO_BOTTOM -> if (topEdge) 0f else 1f
            GradientDirection.BOTTOM_TO_TOP -> if (topEdge) 1f else 0f
            GradientDirection.LEFT_TO_RIGHT,
            GradientDirection.RIGHT_TO_LEFT -> 0.5f
            GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT,
            GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT -> if (topEdge) 0.25f else 0.75f
            GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT,
            GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> if (topEdge) 0.75f else 0.25f
        }
    return Color(
        ColorUtils.blendARGB(
            colors.first().toArgb(),
            colors.last().toArgb(),
            fraction
        )
    )
}

private fun gradientDirectionOffsets(
    direction: GradientDirection,
    size: Size
): Pair<Offset, Offset> {
    val left = 0f
    val top = 0f
    val right = size.width
    val bottom = size.height
    val centerX = size.width / 2f
    val centerY = size.height / 2f

    return when (direction) {
        GradientDirection.TOP_TO_BOTTOM -> Offset(centerX, top) to Offset(centerX, bottom)
        GradientDirection.BOTTOM_TO_TOP -> Offset(centerX, bottom) to Offset(centerX, top)
        GradientDirection.LEFT_TO_RIGHT -> Offset(left, centerY) to Offset(right, centerY)
        GradientDirection.RIGHT_TO_LEFT -> Offset(right, centerY) to Offset(left, centerY)
        GradientDirection.TOP_LEFT_TO_BOTTOM_RIGHT -> Offset(left, top) to Offset(right, bottom)
        GradientDirection.BOTTOM_RIGHT_TO_TOP_LEFT -> Offset(right, bottom) to Offset(left, top)
        GradientDirection.TOP_RIGHT_TO_BOTTOM_LEFT -> Offset(right, top) to Offset(left, bottom)
        GradientDirection.BOTTOM_LEFT_TO_TOP_RIGHT -> Offset(left, bottom) to Offset(right, top)
    }
}

// --- THEME ---

@Composable
fun isPocastCloniDarkTheme(appTheme: AppTheme): Boolean =
    when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

@Composable
fun PocastCloniTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    appColor: AppColor = AppColor.GREEN,
    colorStrength: Float = 0.1f, // Default to a subtle tint
    gradientBackgroundEnabled: Boolean = false,
    gradientBackgroundStrength: Float = 1.0f,
    gradientBackgroundDirection: GradientDirection = GradientDirection.TOP_TO_BOTTOM,
    content: @Composable () -> Unit
) {
    val darkTheme = isPocastCloniDarkTheme(appTheme)

    val seedColor = Color(appColor.hexValue)
    val onSeedColor = seedColor.contrastColor()

    val colorScheme =
        if (darkTheme) {
            val backgroundFactor = 1f - (colorStrength * 0.9f) // Range from 1.0 (black) to 0.1 (strong color)
            val surfaceFactor = 1f - (colorStrength * 0.85f) // Slightly lighter than background

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
                val statusBarColor =
                    if (gradientBackgroundEnabled) {
                        gradientBackgroundSystemBarColor(
                            appColor = appColor,
                            darkTheme = darkTheme,
                            strength = gradientBackgroundStrength,
                            direction = gradientBackgroundDirection,
                            topEdge = true
                        ).toArgb()
                    } else {
                        colorScheme.background.toArgb()
                    }
                val navigationBarColor =
                    if (gradientBackgroundEnabled) {
                        gradientBackgroundSystemBarColor(
                            appColor = appColor,
                            darkTheme = darkTheme,
                            strength = gradientBackgroundStrength,
                            direction = gradientBackgroundDirection,
                            topEdge = false
                        ).toArgb()
                    } else {
                        colorScheme.background.toArgb()
                    }
                window.statusBarColor = statusBarColor
                window.navigationBarColor = navigationBarColor
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }

                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = bestContrastingColor(Color(statusBarColor)) == Color.Black
                    isAppearanceLightNavigationBars = bestContrastingColor(Color(navigationBarColor)) == Color.Black
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

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
