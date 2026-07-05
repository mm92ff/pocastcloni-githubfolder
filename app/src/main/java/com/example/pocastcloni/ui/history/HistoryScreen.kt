package com.example.pocastcloni.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.common.DateBucket
import com.example.pocastcloni.ui.common.ListableEpisodeItem
import com.example.pocastcloni.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState.showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onAction(HistoryAction.DismissClearHistoryDialog) },
            title = { Text(stringResource(id = R.string.confirm_clear_history_title)) },
            text = { Text(stringResource(id = R.string.confirm_clear_history_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onAction(HistoryAction.ConfirmClearHistory) }) {
                    Text(stringResource(id = R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(HistoryAction.DismissClearHistoryDialog) }) {
                    Text(stringResource(id = R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.desc_back)
                        )
                    }
                },
                actions = {
                    if (uiState.historyItems.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onAction(HistoryAction.ClearHistory) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = stringResource(id = R.string.clear_history)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.historyItems.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.history_empty),
                    color = MaterialTheme.colorScheme.onBackground
                )
            } else {
                val bottomPadding =
                    remember(
                        uiState.isPlayerVisible,
                        uiState.navBarHeight,
                        uiState.progressBarHeight
                    ) {
                        if (uiState.isPlayerVisible) {
                            (uiState.navBarHeight + uiState.progressBarHeight).dp + Dimens.PaddingSmall
                        } else {
                            Dimens.PaddingSmall
                        }
                    }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                    PaddingValues(
                        top = Dimens.PaddingSmall,
                        bottom = bottomPadding
                    ),
                    verticalArrangement = if (uiState.oneHandedMode) Arrangement.Bottom else Arrangement.Top
                ) {
                    items(
                        items = uiState.historyRows,
                        key = { row -> row.key }
                    ) { row ->
                        when (row) {
                            is HistoryListRow.SectionHeader -> {
                                HistorySectionHeader(bucket = row.bucket)
                            }

                            is HistoryListRow.EpisodeRow -> {
                                val item = row.item
                                ListableEpisodeItem(
                                    episode = item.episode,
                                    podcast = item.podcast,
                                    showPublishDate = true,
                                    transparentBackground = uiState.transparentEpisodeRows,
                                    onClick = { viewModel.onAction(HistoryAction.OnEpisodeClick(item.episode.guid)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySectionHeader(bucket: DateBucket) {
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = Dimens.PaddingMedium,
                vertical = Dimens.PaddingSmall
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = bucket.labelResId),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier =
            Modifier
                .padding(start = Dimens.PaddingSmall)
                .weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

private val DateBucket.labelResId: Int
    get() =
        when (this) {
            DateBucket.TODAY -> R.string.history_section_today
            DateBucket.YESTERDAY -> R.string.history_section_yesterday
            DateBucket.LAST_WEEK -> R.string.history_section_last_week
            DateBucket.LAST_MONTH -> R.string.history_section_last_month
            DateBucket.LAST_TWO_MONTHS -> R.string.history_section_last_two_months
            DateBucket.LAST_FIVE_MONTHS -> R.string.history_section_last_five_months
            DateBucket.LAST_YEAR -> R.string.history_section_last_year
            DateBucket.OLDER -> R.string.history_section_older
        }
