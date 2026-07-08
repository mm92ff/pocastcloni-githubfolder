package com.example.pocastcloni.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.ui.theme.gradientBackgroundBrush

@Composable
internal fun AppGradientBackground(
    userSettings: UserSettings,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val backgroundModifier =
        if (userSettings.gradientBackgroundEnabled) {
            Modifier.drawWithCache {
                val brush =
                    gradientBackgroundBrush(
                        appColor = userSettings.appColor,
                        darkTheme = darkTheme,
                        strength = userSettings.gradientBackgroundStrength,
                        direction = userSettings.gradientBackgroundDirection,
                        rootSize = size
                    )
                onDrawBehind { drawRect(brush) }
            }
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        content()
    }
}
