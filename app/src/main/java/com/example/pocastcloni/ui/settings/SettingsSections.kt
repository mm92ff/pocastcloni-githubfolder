package com.example.pocastcloni.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.Constants.SettingsDefaults
import com.example.pocastcloni.util.formatBytes
import com.example.pocastcloni.util.formatDuration

// --- SECTIONS ---

@Composable
fun SectionAddPodcast(
    urlInput: String,
    isAdding: Boolean,
    allowInsecureHttp: Boolean,
    allowLocalNetwork: Boolean,
    message: UiText?,
    isError: Boolean,
    onUrlChange: (String) -> Unit,
    onAllowInsecureHttpChange: (Boolean) -> Unit,
    onAllowLocalNetworkChange: (Boolean) -> Unit,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    SettingsSectionTitle(stringResource(R.string.title_add_podcast))

    SettingsCard {
        OutlinedTextField(
            value = urlInput,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.label_rss_url)) },
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = isError
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = allowInsecureHttp,
                onCheckedChange = onAllowInsecureHttpChange
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.allow_legacy_http_media))
                Text(
                    stringResource(R.string.allow_legacy_http_media_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = allowLocalNetwork,
                onCheckedChange = onAllowLocalNetworkChange
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.allow_local_network_feed))
                Text(
                    stringResource(R.string.allow_local_network_feed_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        Button(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = urlInput.isNotBlank() && !isAdding,
            shape = RoundedCornerShape(Dimens.PaddingSmall)
        ) {
            if (isAdding) {
                CircularProgressIndicator(modifier = Modifier.size(Dimens.PaddingLarge), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(Dimens.PaddingVerySmall))
                Text(stringResource(R.string.btn_add))
            }
        }

        if (message != null) {
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
            Text(
                text = message.asString(context),
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
@Suppress("LongMethod", "LongParameterList")
fun SectionAutomation(
    autoRefreshOnStart: Boolean,
    backgroundCheckEnabled: Boolean,
    backgroundCheckInterval: Int,
    feedUpdateMode: FeedUpdateMode,
    smartStreamItemLimit: Int,
    onToggleAutoRefreshOnStart: (Boolean) -> Unit,
    onToggleBackgroundCheck: (Boolean) -> Unit,
    onSetBackgroundCheckInterval: (Int) -> Unit,
    onSetFeedUpdateMode: (FeedUpdateMode) -> Unit,
    onSetSmartStreamItemLimit: (Int) -> Unit,
    onStartManualDownload: () -> Unit
) {
    SettingsSectionTitle(stringResource(R.string.settings_section_automation))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_auto_refresh_on_start),
        subtitle = stringResource(R.string.settings_auto_refresh_on_start_subtitle),
        checked = autoRefreshOnStart,
        onCheckedChange = onToggleAutoRefreshOnStart
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_background_check),
        subtitle = stringResource(R.string.settings_background_check_subtitle),
        checked = backgroundCheckEnabled,
        onCheckedChange = onToggleBackgroundCheck
    )

    if (backgroundCheckEnabled) {
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        SettingsSliderCard(
            title = stringResource(R.string.settings_check_interval),
            value = backgroundCheckInterval,
            valueRange =
            SettingsDefaults.MIN_BACKGROUND_CHECK_INTERVAL_HOURS.rangeTo(
                SettingsDefaults.MAX_BACKGROUND_CHECK_INTERVAL_HOURS
            ),
            steps = (SettingsDefaults.MAX_BACKGROUND_CHECK_INTERVAL_HOURS - SettingsDefaults.MIN_BACKGROUND_CHECK_INTERVAL_HOURS - 1).toInt(),
            onValueChangeFinished = onSetBackgroundCheckInterval,
            valueDisplay = { Text(stringResource(R.string.settings_unit_hours, it)) }
        )
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsCard {
        Text(stringResource(R.string.settings_update_method), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSetFeedUpdateMode(FeedUpdateMode.ALWAYS_FULL) }
        ) {
            RadioButton(
                selected = feedUpdateMode == FeedUpdateMode.ALWAYS_FULL,
                onClick = { onSetFeedUpdateMode(FeedUpdateMode.ALWAYS_FULL) }
            )
            Text(stringResource(R.string.settings_update_method_full), modifier = Modifier.padding(start = Dimens.PaddingVerySmall))
        }
        Spacer(modifier = Modifier.height(Dimens.PaddingTiny))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSetFeedUpdateMode(FeedUpdateMode.SMART_STREAM) }
        ) {
            RadioButton(
                selected = feedUpdateMode == FeedUpdateMode.SMART_STREAM,
                onClick = { onSetFeedUpdateMode(FeedUpdateMode.SMART_STREAM) }
            )
            Column(modifier = Modifier.padding(start = Dimens.PaddingVerySmall)) {
                Text(stringResource(R.string.settings_update_method_smart_stream))
                Text(
                    stringResource(R.string.settings_update_method_smart_stream_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (feedUpdateMode == FeedUpdateMode.SMART_STREAM) {
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        SettingsSliderCard(
            title = stringResource(R.string.settings_smart_stream_item_limit),
            value = smartStreamItemLimit,
            valueRange =
            Constants.Preferences.MIN_SMART_STREAM_ITEM_LIMIT.toFloat().rangeTo(
                Constants.Preferences.MAX_SMART_STREAM_ITEM_LIMIT.toFloat()
            ),
            steps =
            Constants.Preferences.MAX_SMART_STREAM_ITEM_LIMIT -
                Constants.Preferences.MIN_SMART_STREAM_ITEM_LIMIT - 1,
            onValueChangeFinished = onSetSmartStreamItemLimit,
            valueDisplay = { limit ->
                Text(
                    if (limit == Constants.Preferences.DEFAULT_SMART_STREAM_ITEM_LIMIT) {
                        stringResource(R.string.settings_smart_stream_item_limit_full)
                    } else {
                        stringResource(R.string.settings_smart_stream_item_limit_entries, limit)
                    }
                )
            }
        )
        Text(
            text = stringResource(R.string.settings_smart_stream_item_limit_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.PaddingSmall)
        )
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
    ManualFullRefreshButton(onClick = onStartManualDownload)
}

@Composable
private fun ManualFullRefreshButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = Dimens.PaddingMedium,
            vertical = Dimens.PaddingSmall
        )
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(Dimens.PaddingLarge)
        )
        Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
        Column(modifier = Modifier.weight(Constants.Weights.FULL)) {
            Text(
                text = stringResource(R.string.settings_manual_full_refresh),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.settings_manual_full_refresh_subtitle),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun SectionBackup(
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    SettingsSectionTitle(stringResource(R.string.settings_section_backup_restore))

    SettingsCard(onClick = onExport) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Upload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
            Text(stringResource(R.string.settings_export_backup), style = MaterialTheme.typography.titleMedium)
        }
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsCard(onClick = onImport) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
            Text(stringResource(R.string.settings_import_backup), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SectionStatistics(
    statsState: StatisticsScreenUiState,
    onResetStatistics: () -> Unit
) {
    val context = LocalContext.current
    SettingsSectionTitle(stringResource(R.string.settings_section_statistics))

    SettingsCard {
        when (statsState) {
            is StatisticsScreenUiState.Success -> {
                if (statsState.statisticsStartedAt > 0L) {
                    val formatLocale = Locale.getDefault(Locale.Category.FORMAT)
                    val dateFormat = remember(formatLocale) {
                        DateFormat.getDateInstance(DateFormat.MEDIUM, formatLocale)
                    }
                    val startDate = remember(statsState.statisticsStartedAt, formatLocale) {
                        dateFormat.format(Date(statsState.statisticsStartedAt))
                    }
                    val daysActive = remember(statsState.statisticsStartedAt) {
                        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - statsState.statisticsStartedAt)
                    }
                    val daysLabel = if (daysActive == 1L) {
                        stringResource(R.string.settings_statistics_days_singular)
                    } else {
                        stringResource(R.string.settings_statistics_days_plural)
                    }
                    InfoRow(
                        label = stringResource(R.string.settings_statistics_running_since),
                        value = "$startDate ($daysActive $daysLabel)"
                    )
                    Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                }

                val formattedDuration = formatDuration(context, statsState.totalPlayTimeMs / 1000)
                InfoRow(stringResource(R.string.settings_statistics_total_listening_time), formattedDuration)
                InfoRow(stringResource(R.string.settings_statistics_total_episodes), statsState.totalEpisodes.toString())
                InfoRow(stringResource(R.string.settings_statistics_episodes_in_progress), statsState.episodesInProgress.toString())
                InfoRow(stringResource(R.string.settings_statistics_episodes_played), statsState.episodesPlayed.toString())

                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

                InfoRow(
                    label = stringResource(R.string.settings_statistics_downloads_wifi),
                    value = formatBytes(context, statsState.downloadWifiBytes)
                )
                InfoRow(
                    label = stringResource(R.string.settings_statistics_downloads_mobile),
                    value = formatBytes(context, statsState.downloadMobileBytes)
                )
                InfoRow(
                    label = stringResource(R.string.settings_statistics_streaming_wifi),
                    value = formatBytes(context, statsState.streamWifiBytes)
                )
                InfoRow(
                    label = stringResource(R.string.settings_statistics_streaming_mobile),
                    value = formatBytes(context, statsState.streamMobileBytes)
                )

                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

                Button(
                    onClick = onResetStatistics,
                    modifier = Modifier.align(Alignment.End),
                    colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(Dimens.PaddingMedium))
                    Spacer(modifier = Modifier.width(Dimens.PaddingVerySmall))
                    Text(stringResource(R.string.reset))
                }
            }
            is StatisticsScreenUiState.Loading -> {
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Dimens.PaddingLarge),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is StatisticsScreenUiState.Error -> {
                Text(
                    text = statsState.message.asString(context),
                    modifier = Modifier.padding(Dimens.PaddingMedium),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVeryLarge))
}
