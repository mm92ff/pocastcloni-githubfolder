package com.example.pocastcloni.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartStreamSliderMappingTest {
    @Test
    fun `presets contain zero through twenty and the exact extended values`() {
        assertEquals(
            (0..20).toList() + listOf(25, 32, 40, 50, 64, 80, 100),
            SMART_STREAM_ITEM_LIMIT_PRESETS
        )
        assertEquals(SMART_STREAM_ITEM_LIMIT_PRESETS.size - 2, SMART_STREAM_SLIDER_STEPS)
    }

    @Test
    fun `every preset round trips through its slider position`() {
        SMART_STREAM_ITEM_LIMIT_PRESETS.forEachIndexed { index, itemLimit ->
            val position = smartStreamItemLimitToSliderPosition(itemLimit)

            assertEquals(index.toFloat(), position, 0.0001f)
            assertEquals(itemLimit, smartStreamSliderPositionToItemLimit(position))
        }
    }

    @Test
    fun `non preset thirty seven is interpolated without changing its source value`() {
        val position = smartStreamItemLimitToSliderPosition(37)

        assertEquals(22.625f, position, 0.0001f)
        assertTrue(position > smartStreamItemLimitToSliderPosition(32))
        assertTrue(position < smartStreamItemLimitToSliderPosition(40))
        assertEquals(40, smartStreamSliderPositionToItemLimit(position))
    }
}
