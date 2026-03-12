package com.example.pocastcloni.ui.home.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer // NEU: Wichtig für Performance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants

@Composable
fun DeleteConfirmDialog(podcastTitle: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_delete_title)) },
        text = { Text(stringResource(R.string.dialog_delete_download_msg, podcastTitle)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.btn_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteItem(
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    confirmDismiss: Boolean = true,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                confirmDismiss
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(Dimens.RoundedCornerLarge))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = Dimens.PaddingExtra),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_podcast_icon),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = { content() }
    )
}

@Composable
fun BoxScope.IndicatorDot(
    xOffset: Int,
    yOffset: Int,
    borderWidth: Int,
    size: Int,
    colorArgb: Long,
    modifier: Modifier = Modifier
) {
    // PERFORMANCE: Wir konvertieren hier vorab in Dp, nutzen es aber im graphicsLayer
    val xOffsetDp = xOffset.dp
    val yOffsetDp = (-yOffset).dp
    val borderWidthDp = borderWidth.dp
    val totalSize = size.dp + (borderWidthDp * 2)

    Box(
        modifier = modifier
            .align(Alignment.TopEnd)
            .zIndex(Constants.UI.INDICATOR_Z_INDEX)
            // PERFORMANCE FIX: Statt .offset() nutzen wir graphicsLayer.
            // Das verhindert Layout-Neuberechnungen (Measure/Layout Phase) und nutzt direkt die GPU (Draw Phase).
            .graphicsLayer {
                translationX = xOffsetDp.toPx()
                translationY = yOffsetDp.toPx()
            }
            .size(totalSize)
            .clip(CircleShape)
            // .background ist die Farbe des App-Hintergrunds für den "Cutout"-Effekt
            .background(MaterialTheme.colorScheme.background)
            .padding(borderWidthDp)
            .background(Color(colorArgb), CircleShape)
    )
}

@Composable
fun DeleteIconOverlay(modifier: Modifier, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .offset(x = Dimens.PaddingMedium, y = (-Dimens.PaddingMedium))
            .zIndex(Constants.UI.DELETE_ICON_Z_INDEX)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error)
            .size(Dimens.LargeIconSize)
            .border(Dimens.BorderWidthDefault, MaterialTheme.colorScheme.onError, CircleShape)
    ) {
        Icon(
            Icons.Default.Close,
            contentDescription = stringResource(R.string.delete_podcast_icon),
            tint = MaterialTheme.colorScheme.onError,
            modifier = Modifier.size(Dimens.SmallIconSize)
        )
    }
}