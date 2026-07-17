package com.example.pocastcloni.ui.settings

import kotlin.math.roundToInt

@Suppress("MagicNumber")
internal val SMART_STREAM_ITEM_LIMIT_PRESETS: List<Int> =
    (0..20).toList() + listOf(25, 32, 40, 50, 64, 80, 100)

internal val SMART_STREAM_SLIDER_RANGE: ClosedFloatingPointRange<Float> =
    0f..SMART_STREAM_ITEM_LIMIT_PRESETS.lastIndex.toFloat()

internal val SMART_STREAM_SLIDER_STEPS = SMART_STREAM_ITEM_LIMIT_PRESETS.size - 2

internal fun smartStreamItemLimitToSliderPosition(itemLimit: Int): Float {
    val boundedLimit = itemLimit.coerceIn(
        SMART_STREAM_ITEM_LIMIT_PRESETS.first(),
        SMART_STREAM_ITEM_LIMIT_PRESETS.last()
    )
    val exactIndex = SMART_STREAM_ITEM_LIMIT_PRESETS.binarySearch(boundedLimit)
    if (exactIndex >= 0) return exactIndex.toFloat()

    val upperIndex = -exactIndex - 1
    val lowerIndex = upperIndex - 1
    val lowerLimit = SMART_STREAM_ITEM_LIMIT_PRESETS[lowerIndex]
    val upperLimit = SMART_STREAM_ITEM_LIMIT_PRESETS[upperIndex]
    val fraction = (boundedLimit - lowerLimit).toFloat() / (upperLimit - lowerLimit)
    return lowerIndex + fraction
}

internal fun smartStreamSliderPositionToItemLimit(sliderPosition: Float): Int =
    SMART_STREAM_ITEM_LIMIT_PRESETS[
        sliderPosition.roundToInt().coerceIn(0, SMART_STREAM_ITEM_LIMIT_PRESETS.lastIndex)
    ]
