package com.example.pocastcloni.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween

object Motion {
    const val DurationShort = 120
    const val DurationMedium = 220
    const val DurationLong = 320

    val StandardEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val ExitEasing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    fun <T> stateSpec(durationMillis: Int = DurationMedium): TweenSpec<T> =
        tween(durationMillis = durationMillis, easing = StandardEasing)

    fun <T> enterSpec(durationMillis: Int = DurationMedium): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedEasing)

    fun <T> exitSpec(durationMillis: Int = DurationShort): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = ExitEasing)
}
