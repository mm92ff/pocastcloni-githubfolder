package com.example.pocastcloni.sprint11

import androidx.compose.ui.graphics.Color
import com.example.pocastcloni.domain.model.AppColor
import com.example.pocastcloni.ui.theme.bestContrastingColor
import com.example.pocastcloni.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

class Sprint11ContrastTest {
    @Test
    fun `every offered app color has normal text contrast`() {
        AppColor.entries.forEach { appColor ->
            val background = Color(appColor.hexValue)
            val foreground = bestContrastingColor(background)
            val ratio = contrastRatio(foreground, background)

            assertTrue("${appColor.name} contrast was $ratio", ratio >= 4.5)
        }
    }
}

