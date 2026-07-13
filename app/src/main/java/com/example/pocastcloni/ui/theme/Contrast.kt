package com.example.pocastcloni.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

private const val WcagLuminanceOffset = 0.05

fun contrastRatio(
    foreground: Color,
    background: Color
): Double {
    val opaqueBackground = background.compositeOver(Color.White)
    val opaqueForeground = foreground.compositeOver(opaqueBackground)
    val lighter = maxOf(opaqueForeground.luminance(), opaqueBackground.luminance()).toDouble()
    val darker = minOf(opaqueForeground.luminance(), opaqueBackground.luminance()).toDouble()
    return (lighter + WcagLuminanceOffset) / (darker + WcagLuminanceOffset)
}

fun bestContrastingColor(
    background: Color,
    dark: Color = Color.Black,
    light: Color = Color.White
): Color =
    if (contrastRatio(dark, background) >= contrastRatio(light, background)) {
        dark
    } else {
        light
    }

