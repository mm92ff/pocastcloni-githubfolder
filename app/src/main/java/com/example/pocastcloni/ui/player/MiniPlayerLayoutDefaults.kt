package com.example.pocastcloni.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pocastcloni.ui.theme.Dimens

object MiniPlayerLayoutDefaults {
    val BorderWidth = 1.dp

    fun collapsedHeight(progressBarHeight: Dp): Dp {
        return Dimens.MiniPlayerImageSize +
            (Dimens.PaddingVerySmall * 4) +
            progressBarHeight
    }

    fun reservedBottomPadding(
        isPlayerVisible: Boolean,
        navBarHeight: Int,
        progressBarHeight: Int,
        extraPadding: Dp
    ): Dp {
        return if (isPlayerVisible) {
            navBarHeight.dp +
                collapsedHeight(progressBarHeight.dp) +
                extraPadding
        } else {
            extraPadding
        }
    }
}
