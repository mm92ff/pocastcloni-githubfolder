package com.example.pocastcloni.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens

@Composable
fun FullPlayerControls(
    playerState: PlayerUiState,
    onEvent: (PlayerScreenEvent) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(Dimens.LargeIconSize))

        IconButton(
            onClick = { onEvent(PlayerScreenEvent.Rewind) },
            modifier = Modifier.size(Dimens.LargeIconSize)
        ) {
            Icon(
                imageVector = Icons.Default.Replay10,
                contentDescription = stringResource(R.string.desc_rewind),
                modifier = Modifier.size(Dimens.LargeIconSize)
            )
        }

        Box(
            modifier = Modifier.size(Dimens.PlayPauseButtonSize),
            contentAlignment = Alignment.Center
        ) {
            if (playerState.isBuffering) {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.LargeIconSize))
            } else {
                IconButton(
                    onClick = { onEvent(PlayerScreenEvent.TogglePlayPause) },
                    modifier = Modifier.size(Dimens.PlayPauseButtonSize)
                ) {
                    Icon(
                        imageVector =
                        if (playerState.isPlaying) {
                            Icons.Default.PauseCircleFilled
                        } else {
                            Icons.Default.PlayCircleFilled
                        },
                        contentDescription = stringResource(R.string.desc_play_pause),
                        modifier = Modifier.size(Dimens.PlayPauseButtonSize),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        IconButton(
            onClick = { onEvent(PlayerScreenEvent.Forward) },
            modifier = Modifier.size(Dimens.LargeIconSize)
        ) {
            Icon(
                imageVector = Icons.Default.Forward30,
                contentDescription = stringResource(R.string.desc_fast_forward),
                modifier = Modifier.size(Dimens.LargeIconSize)
            )
        }

        IconButton(
            onClick = { onEvent(PlayerScreenEvent.ToggleFavorite) },
            modifier = Modifier.size(Dimens.LargeIconSize)
        ) {
            Icon(
                imageVector =
                if (playerState.isCurrentEpisodeFavorite) {
                    Icons.Default.Favorite
                } else {
                    Icons.Default.FavoriteBorder
                },
                contentDescription = stringResource(R.string.favorites),
                modifier = Modifier.size(Dimens.LargeIconSize),
                tint =
                if (playerState.isCurrentEpisodeFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
