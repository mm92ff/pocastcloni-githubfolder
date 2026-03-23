package com.example.pocastcloni.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.parseHtml

@Composable
fun EpisodeDetailsDialog(
    episodeTitle: String,
    podcastTitle: String,
    episodeDescription: String,
    onDismissRequest: () -> Unit
) {
    // PERFORMANCE FIX: HTML Parsing cachen
    // Wird nur neu berechnet, wenn sich episodeDescription ändert.
    val formattedDescription =
        remember(episodeDescription) {
            episodeDescription.parseHtml().toString()
        }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = Dimens.DialogMaxHeight)
        ) {
            Column(modifier = Modifier.padding(Dimens.PaddingLarge)) {
                Text(
                    text = episodeTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
                Text(
                    text = podcastTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

                Column(modifier = Modifier.weight(1f)) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = formattedDescription, // FIX: Nutzung des gecachten Wertes
                        style = MaterialTheme.typography.bodyMedium,
                        modifier =
                        Modifier
                            .verticalScroll(scrollState)
                    )
                }

                Spacer(modifier = Modifier.height(Dimens.PaddingLarge))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismissRequest) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}
