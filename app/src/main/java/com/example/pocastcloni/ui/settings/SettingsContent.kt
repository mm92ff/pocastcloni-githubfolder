package com.example.pocastcloni.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.player.MiniPlayerLayoutDefaults
import com.example.pocastcloni.ui.theme.Dimens
import kotlinx.coroutines.launch

private enum class SettingsTab(@StringRes val labelRes: Int) {
    DESIGN(R.string.settings_tab_design),
    PLAYBACK(R.string.settings_tab_playback),
    SYNC_STORAGE(R.string.settings_tab_sync),
    DATA(R.string.settings_tab_data)
}

/**
 * Builds the full settings list.
 * PERFORMANCE-OPTIMISED:
 * 1. Stable lazy keys
 * 2. Granular state passing (no monolith)
 * 3. Stabilised lambdas
 */
@Composable
fun SettingsListContent(
    settings: SettingsUiState.Success,
    downloadMessage: UiText?,
    isPlayerVisible: Boolean,
    onEvent: (SettingsUiEvent) -> Unit, // Must be stable (method reference)
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.DESIGN) }
    val tabs = SettingsTab.entries
    val designListState = rememberLazyListState()
    val playbackListState = rememberLazyListState()
    val syncListState = rememberLazyListState()
    val dataListState = rememberLazyListState()
    val listStates =
        mapOf(
            SettingsTab.DESIGN to designListState,
            SettingsTab.PLAYBACK to playbackListState,
            SettingsTab.SYNC_STORAGE to syncListState,
            SettingsTab.DATA to dataListState
        )
    val listState = listStates.getValue(selectedTab)
    val coroutineScope = rememberCoroutineScope()
    val bottomPadding =
        MiniPlayerLayoutDefaults.reservedBottomPadding(
            isPlayerVisible = isPlayerVisible,
            progressBarHeight = settings.progressBarHeight,
            extraPadding = Dimens.PaddingMedium
        )

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOf(selectedTab),
            modifier = Modifier.fillMaxWidth(),
            edgePadding = Dimens.PaddingSmall
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = {
                        if (selectedTab != tab) {
                            selectedTab = tab
                            coroutineScope.launch {
                                listStates.getValue(tab).scrollToItem(0)
                            }
                        }
                    },
                    text = { Text(stringResource(tab.labelRes)) }
                )
            }
        }

        CompositionLocalProvider(LocalTransparentSettingsCards provides settings.transparentSearchCards) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("settings-list-${selectedTab.name.lowercase()}"),
                state = listState,
                contentPadding =
                PaddingValues(
                    start = Dimens.PaddingMedium,
                    top = Dimens.PaddingMedium,
                    end = Dimens.PaddingMedium,
                    bottom = bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.PaddingLarge)
            ) {
                item(key = selectedTab.name) {
                    when (selectedTab) {
                        SettingsTab.DESIGN -> DesignSettingsContent(settings = settings, onEvent = onEvent)
                        SettingsTab.PLAYBACK -> PlaybackSettingsContent(settings = settings, onEvent = onEvent)
                        SettingsTab.SYNC_STORAGE ->
                            SyncStorageSettingsContent(
                                settings = settings,
                                downloadMessage = downloadMessage,
                                onEvent = onEvent
                            )
                        SettingsTab.DATA ->
                            DataSettingsContent(
                                onEvent = onEvent,
                                onExportClick = onExportClick,
                                onImportClick = onImportClick
                            )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsTabDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(top = Dimens.PaddingMedium),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
        thickness = Dimens.ThicknessDefault
    )
}
