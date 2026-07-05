package com.example.pocastcloni.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.ui.theme.gradientBackgroundBottomColor

@Composable
internal fun AppGradientBackground(
    userSettings: UserSettings,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val backgroundModifier =
        if (userSettings.gradientBackgroundEnabled) {
            Modifier.background(
                Brush.verticalGradient(
                    colors =
                    listOf(
                        if (darkTheme) Color.Black else Color.White,
                        gradientBackgroundBottomColor(
                            appColor = userSettings.appColor,
                            darkTheme = darkTheme,
                            strength = userSettings.gradientBackgroundStrength
                        )
                    )
                )
            )
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
