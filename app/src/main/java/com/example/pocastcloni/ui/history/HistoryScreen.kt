package com.example.pocastcloni.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
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
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
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
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.historyItems.isEmpty()) {
                // FIX: Hardcoded String entfernt (bitte sicherstellen, dass String-Resource existiert)
                Text(text = stringResource(id = R.string.history_empty))
            } else {
                // FIX: Keine 'remember' Logik mehr nötig für Listen-Konvertierung

                val bottomPadding = remember(
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
                    contentPadding = PaddingValues(
                        top = Dimens.PaddingSmall,
                        bottom = bottomPadding
                    ),
                    reverseLayout = uiState.oneHandedMode
                ) {
                    items(
                        items = uiState.historyItems, // Direkter Zugriff auf optimierte Liste
                        key = { item -> item.id }
                    ) { item ->
                        ListableEpisodeItem(
                            episode = item.episode,
                            podcast = item.podcast,
                            onClick = { viewModel.onAction(HistoryAction.OnEpisodeClick(item.episode.guid)) }
                        )
                    }
                }
            }
        }
    }
}
