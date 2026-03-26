package com.example.pocastcloni.ui.home.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import coil.compose.AsyncImage
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

@Composable
fun SearchArea(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    isSearching: Boolean,
    onSearchTriggered: () -> Unit,
    searchError: String?,
    keyboardController: SoftwareKeyboardController?
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.label_input)) },
                modifier =
                Modifier
                    .weight(Constants.Weights.FULL)
                    .heightIn(min = Dimens.SearchFieldMinHeight),
                singleLine = true,
                shape = CircleShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                KeyboardActions(
                    onSearch = {
                        onSearchTriggered()
                        keyboardController?.hide()
                    }
                ),
                colors =
                TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Button(
                onClick = {
                    onSearchTriggered()
                    keyboardController?.hide()
                },
                modifier =
                Modifier
                    .size(Dimens.SearchFieldMinHeight)
                    .align(Alignment.CenterVertically),
                shape = CircleShape,
                enabled = !isSearching,
                contentPadding = PaddingValues(Dimens.Zero)
            ) {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.MediumIconSize),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = Dimens.BorderWidthDefault
                    )
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            }
        }

        if (searchError != null) {
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            Text(
                text = searchError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = Dimens.PaddingMedium)
            )
        }
    }
}

@Composable
fun SearchResultsList(
    results: ImmutableList<PodcastSearchResult>,
    subscribedUrls: ImmutableSet<String>,
    onToggleClick: (PodcastSearchResult) -> Unit,
    reverseLayout: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = reverseLayout,
        contentPadding = PaddingValues(vertical = Dimens.SearchResultsListVerticalPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SearchResultsItemSpacing)
    ) {
        items(
            items = results,
            key = { it.feedUrl }
        ) { podcast ->
            val isSubscribed = subscribedUrls.contains(podcast.feedUrl)
            PodcastSearchItem(
                podcast = podcast,
                isSubscribed = isSubscribed,
                onToggle = { onToggleClick(podcast) }
            )
        }
    }
}

@Composable
fun PodcastSearchItem(
    podcast: PodcastSearchResult,
    isSubscribed: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SearchCardOuterPaddingVertical),
        elevation = CardDefaults.cardElevation(defaultElevation = Dimens.CardElevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(Dimens.SearchCardContentPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = podcast.artworkUrl,
                contentDescription = stringResource(R.string.desc_cover),
                modifier =
                Modifier
                    .size(Dimens.SearchImageSize)
                    .clip(RoundedCornerShape(Dimens.RoundedCornerMedium))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(Dimens.SearchCardGapBetweenImageAndText))

            Column(modifier = Modifier.weight(Constants.Weights.FULL)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = Constants.UI.PODCAST_SEARCH_ITEM_MAX_LINES,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = Constants.UI.PODCAST_SEARCH_ITEM_MAX_LINES,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onToggle) {
                if (isSubscribed) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.subscribed),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.LargeIconSize)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = stringResource(R.string.add),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.LargeIconSize)
                    )
                }
            }
        }
    }
}
