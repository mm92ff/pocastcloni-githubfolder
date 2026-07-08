package com.example.pocastcloni.ui.player

import android.text.Spanned
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.toSize
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.ui.theme.Motion
import com.example.pocastcloni.ui.theme.gradientBackgroundBrush
import com.example.pocastcloni.ui.theme.isPocastCloniDarkTheme
import kotlinx.coroutines.flow.StateFlow

@Composable
fun ExpandablePlayer(
    playerState: PlayerUiState,
    userSettings: UserSettings,
    playbackStateFlow: StateFlow<PlaybackState>,
    episodeDescription: Spanned?,
    isDescriptionVisible: Boolean,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onEvent: (PlayerScreenEvent) -> Unit,
    onNavigateToPodcastDetail: (podcastUrl: String) -> Unit,
    progressBarHeight: Dp,
    showMiniPlayerTimeOverlay: Boolean,
    transparentMiniPlayer: Boolean,
    navBarHeight: Dp,
    modifier: Modifier = Modifier
) {
    val playerShape =
        if (isExpanded) {
            RoundedCornerShape(Dimens.Zero)
        } else {
            RoundedCornerShape(topStart = Dimens.PaddingLarge, topEnd = Dimens.PaddingLarge)
        }
    val transparentBackdropModifier =
        transparentMiniPlayerBackdropModifier(
            enabled = !isExpanded && transparentMiniPlayer,
            userSettings = userSettings,
            shape = playerShape
        )

    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .then(if (isExpanded) Modifier.fillMaxSize() else Modifier.wrapContentHeight())
            .then(transparentBackdropModifier)
            .animateContentSize(animationSpec = Motion.enterSpec()),
        shape = playerShape,
        shadowElevation = if (isExpanded || transparentMiniPlayer) Dimens.Zero else Dimens.PaddingLarge,
        color =
        if (isExpanded) {
            Color.Transparent
        } else if (transparentMiniPlayer) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor =
        if (isExpanded || transparentMiniPlayer) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                fadeIn(animationSpec = Motion.enterSpec()) togetherWith
                    fadeOut(animationSpec = Motion.exitSpec())
            },
            label = "playerExpansionContent"
        ) { expanded ->
            if (expanded) {
                FullPlayerScreen(
                    playerState = playerState,
                    userSettings = userSettings,
                    playbackStateFlow = playbackStateFlow,
                    episodeDescription = episodeDescription,
                    isDescriptionVisible = isDescriptionVisible,
                    onCollapse = onExpandToggle,
                    onEvent = onEvent,
                    progressBarHeight = progressBarHeight,
                    navBarHeight = navBarHeight
                )
            } else {
                MiniPlayer(
                    playerState = playerState,
                    playbackStateFlow = playbackStateFlow,
                    onExpand = onExpandToggle,
                    onCoverClick = {
                        playerState.currentPodcastUrl?.let {
                            onNavigateToPodcastDetail(it)
                        }
                    },
                    onEvent = onEvent,
                    progressBarHeight = progressBarHeight,
                    showTimeOverlay = showMiniPlayerTimeOverlay,
                    transparentBackground = transparentMiniPlayer
                )
            }
        }
    }
}

@Composable
private fun transparentMiniPlayerBackdropModifier(
    enabled: Boolean,
    userSettings: UserSettings,
    shape: RoundedCornerShape
): Modifier {
    if (!enabled) {
        return Modifier
    }

    var leftInRootPx by remember { mutableFloatStateOf(0f) }
    var topInRootPx by remember { mutableFloatStateOf(0f) }
    var rootWidthPx by remember { mutableFloatStateOf(1f) }
    var rootHeightPx by remember { mutableFloatStateOf(1f) }
    val backgroundModifier =
        Modifier.onGloballyPositioned { coordinates ->
            val positionInRoot = coordinates.positionInRoot()
            val rootSize = coordinates.findRootCoordinates().size.toSize()
            leftInRootPx = positionInRoot.x
            topInRootPx = positionInRoot.y
            rootWidthPx = rootSize.width.coerceAtLeast(1f)
            rootHeightPx = rootSize.height.coerceAtLeast(1f)
        }

    return if (userSettings.gradientBackgroundEnabled) {
        backgroundModifier.background(
            brush =
            gradientBackgroundBrush(
                appColor = userSettings.appColor,
                darkTheme = isPocastCloniDarkTheme(userSettings.theme),
                strength = userSettings.gradientBackgroundStrength,
                direction = userSettings.gradientBackgroundDirection,
                rootSize = Size(rootWidthPx, rootHeightPx),
                offsetInRoot = Offset(leftInRootPx, topInRootPx)
            ),
            shape = shape
        )
    } else {
        backgroundModifier.background(
            color = MaterialTheme.colorScheme.background,
            shape = shape
        )
    }
}
