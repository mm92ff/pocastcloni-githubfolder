package com.example.pocastcloni.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.ui.common.EpisodeDisplayModel
import com.example.pocastcloni.ui.theme.Dimens

@Composable
fun ListableEpisodeItem(
    episode: EpisodeDisplayModel,
    podcast: Podcast?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onImageClick: (() -> Unit)? = null // NEU: Optionaler Callback für Bild-Klick
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Dimens.PaddingLarge, vertical = Dimens.PaddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = episode.podcastImageUrl ?: podcast?.imageUrl ?: "",
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(Dimens.RoundedCornerSmall))
                // NEU: Klickbar machen, falls ein Callback übergeben wird
                .clickable(enabled = onImageClick != null) { onImageClick?.invoke() },
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(Dimens.PaddingLarge))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingExtraSmall))
            Text(
                text = episode.podcastTitle ?: podcast?.title ?: stringResource(id = R.string.unknown_podcast_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
