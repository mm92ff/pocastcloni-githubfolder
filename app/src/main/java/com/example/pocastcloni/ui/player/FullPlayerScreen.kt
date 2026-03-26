package com.example.pocastcloni.ui.player

import android.text.Spanned
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.foundation.layout.Column as LayoutColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    playerState: PlayerUiState,
    playbackStateFlow: StateFlow<PlaybackState>,
    episodeDescription: Spanned?,
    isDescriptionVisible: Boolean,
    onCollapse: () -> Unit,
    onEvent: (PlayerScreenEvent) -> Unit,
    progressBarHeight: Dp,
    @Suppress("UNUSED_PARAMETER") navBarHeight: Dp
) {
    if (isDescriptionVisible) {
        EpisodeDescriptionDialog(
            description = episodeDescription,
            isParsing = false,
            onDismissRequest = { onEvent(PlayerScreenEvent.DismissDescription) }
        )
    }

    val density = LocalDensity.current
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val rootHeightDp = with(density) { rootSize.height.toDp() }

    // Spacing between Progress/Time and Controls adapts to available height
    val progressToControlsSpacing: Dp =
        when {
            rootHeightDp <= 620.dp -> Dimens.PaddingMicro // 2dp
            rootHeightDp <= 720.dp -> Dimens.PaddingTiny // 4dp
            rootHeightDp <= 820.dp -> Dimens.PaddingVerySmall // 8dp
            else -> Dimens.PaddingSmall // 12dp
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.desc_close)
                        )
                    }
                },
                colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LayoutColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Dimens.PaddingLarge)
                .onSizeChanged { rootSize = it },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP (flexible)
            LayoutColumn(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FullPlayerMetadataFlexibleCover(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    playerState = playerState,
                    onEvent = onEvent,
                    onCollapse = onCollapse
                )

                playerState.error?.let { msg ->
                    Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                    FullPlayerErrorBanner(message = msg)
                }
            }

            // Progress + Time
            FullPlayerProgressSection(
                playbackStateFlow = playbackStateFlow,
                isPlaying = playerState.isPlaying,
                progressBarHeight = progressBarHeight,
                onSeek = { onEvent(PlayerScreenEvent.SeekTo(it)) },
                onSeekStart = { onEvent(PlayerScreenEvent.SeekStarted) },
                onSeekEnd = { onEvent(PlayerScreenEvent.SeekFinished) }
            )

            // AUTO spacing
            Spacer(modifier = Modifier.height(progressToControlsSpacing))

            // Controls
            FullPlayerControls(
                playerState = playerState,
                onEvent = onEvent
            )

            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        }
    }
}

@Composable
private fun FullPlayerErrorBanner(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
        Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(Dimens.RoundedCornerSmall)
            )
            .padding(Dimens.PaddingSmall)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(Dimens.MediumIconSize)
        )
        Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}
