package com.example.pocastcloni.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.model.FeedUpdateMode
import com.example.pocastcloni.domain.model.LayoutMode
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
    message: UiText?,
    isError: Boolean,
    onUrlChange: (String) -> Unit,
    onAddClick: () -> Unit
) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.title_add_podcast),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionAppearance(
    theme: AppTheme,
    appColor: AppColor,
    colorStrength: Float,
    onSetTheme: (AppTheme) -> Unit,
    onSetAppColor: (AppColor) -> Unit,
    onSetColorStrength: (Float) -> Unit
) {
    Text(
        stringResource(R.string.settings_section_design),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

    SettingsCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ThemeChip(stringResource(R.string.theme_system), theme == AppTheme.SYSTEM) { onSetTheme(AppTheme.SYSTEM) }
            ThemeChip(stringResource(R.string.theme_light), theme == AppTheme.LIGHT) { onSetTheme(AppTheme.LIGHT) }
            ThemeChip(stringResource(R.string.theme_dark), theme == AppTheme.DARK) { onSetTheme(AppTheme.DARK) }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsCard {
        Text(stringResource(R.string.settings_app_color), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
        ) {
            AppColor.entries.forEach { color ->
                ColorCircle(color.hexValue, appColor == color) { onSetAppColor(color) }
            }
        }
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    var localStrength by remember(colorStrength) { mutableFloatStateOf(colorStrength) }
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = stringResource(R.string.settings_color_strength), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.settings_percentage, localStrength * 100))
        }
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        Slider(
            value = localStrength,
            onValueChange = { localStrength = it },
            onValueChangeFinished = { onSetColorStrength(localStrength) },
            valueRange = 0.0f..1.0f,
            steps = 9,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SectionIndicator(
    gridSizeDp: Int,
    indicatorState: IndicatorSettingsUiState,
    onColorClick: (Long) -> Unit,
    onSizeChange: (Int) -> Unit,
    onBorderChange: (Int) -> Unit,
    onXOffsetChange: (Int) -> Unit,
    onYOffsetChange: (Int) -> Unit
) {
    Text(
        text = stringResource(R.string.settings_notification_dot),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

    SettingsCard {
        // --- 1. FARBAUSWAHL ---
        Text(stringResource(R.string.settings_color), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingSmall))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
        ) {
            // VERWENDUNG DER KONSTANTEN: Constants.UI.INDICATOR_COLORS
            Constants.UI.INDICATOR_COLORS.forEach { colorArg ->
                IndicatorColorCircle(
                    colorArgb = colorArg,
                    isSelected = indicatorState.colorArgb == colorArg,
                    onClick = { onColorClick(colorArg) }
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        // --- 2. VORSCHAU ---
        NotificationDotPreview(
            indicatorState = indicatorState,
            gridSizeDp = gridSizeDp
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        // --- 3. SLIDER MIT KONSTANTEN ---

        // Größe
        SettingsSliderCard(
            title = stringResource(R.string.settings_size),
            value = indicatorState.size,
            valueRange = SettingsDefaults.MIN_INDICATOR_SIZE_DP..SettingsDefaults.MAX_INDICATOR_SIZE_DP,
            onValueChangeFinished = onSizeChange,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        // Rahmenbreite
        SettingsSliderCard(
            title = stringResource(R.string.settings_border_width),
            value = indicatorState.borderWidth,
            valueRange = SettingsDefaults.MIN_INDICATOR_BORDER_DP..SettingsDefaults.MAX_INDICATOR_BORDER_DP,
            onValueChangeFinished = onBorderChange,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        // Horizontaler Versatz
        SettingsSliderCard(
            title = stringResource(R.string.settings_horizontal_offset),
            value = indicatorState.xOffset,
            valueRange = SettingsDefaults.MIN_INDICATOR_OFFSET_DP..SettingsDefaults.MAX_INDICATOR_OFFSET_DP,
            onValueChangeFinished = onXOffsetChange,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        // Vertikaler Versatz
        SettingsSliderCard(
            title = stringResource(R.string.settings_vertical_offset),
            value = indicatorState.yOffset,
            valueRange = SettingsDefaults.MIN_INDICATOR_OFFSET_DP..SettingsDefaults.MAX_INDICATOR_OFFSET_DP,
            onValueChangeFinished = onYOffsetChange,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )
    }
}

@Composable
fun SectionAutomation(
    autoRefreshOnStart: Boolean,
    backgroundCheckEnabled: Boolean,
    backgroundCheckInterval: Int,
    feedUpdateMode: FeedUpdateMode,
    onToggleAutoRefreshOnStart: (Boolean) -> Unit,
    onToggleBackgroundCheck: (Boolean) -> Unit,
    onSetBackgroundCheckInterval: (Int) -> Unit,
    onSetFeedUpdateMode: (FeedUpdateMode) -> Unit
) {
    Text(
        stringResource(R.string.settings_section_automation),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

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
            valueRange = SettingsDefaults.MIN_BACKGROUND_CHECK_INTERVAL_HOURS..SettingsDefaults.MAX_BACKGROUND_CHECK_INTERVAL_HOURS,
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
}

@Composable
fun SectionDownloads(
    autoDownloadLimit: Int,
    message: UiText?,
    onSetAutoDownloadLimit: (Int) -> Unit,
    onStartManualDownload: () -> Unit
) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.settings_section_downloads_data),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

    SettingsSliderCard(
        title = stringResource(R.string.settings_auto_download_limit),
        value = autoDownloadLimit,
        valueRange = SettingsDefaults.MIN_AUTO_DOWNLOAD_LIMIT..SettingsDefaults.MAX_AUTO_DOWNLOAD_LIMIT,
        steps = (SettingsDefaults.MAX_AUTO_DOWNLOAD_LIMIT - SettingsDefaults.MIN_AUTO_DOWNLOAD_LIMIT - 1).toInt(),
        onValueChangeFinished = onSetAutoDownloadLimit,
        valueDisplay = { Text(stringResource(R.string.settings_unit_episodes, it)) }
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsCard(onClick = onStartManualDownload) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
            Column {
                Text(stringResource(R.string.settings_manual_full_refresh), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_manual_full_refresh_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (message != null) {
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        Text(
            text = message.asString(context),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SectionBackup(
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Text(
        stringResource(R.string.settings_section_backup_restore),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

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
fun SectionInterface(
    layoutMode: LayoutMode,
    gridSize: Int,
    showGridTitles: Boolean,
    oneHandedMode: Boolean,
    progressBarHeight: Int,
    navBarHeight: Int,
    confirmDelete: Boolean,
    onSetLayoutMode: (LayoutMode) -> Unit,
    onSetGridSize: (Int) -> Unit,
    onToggleShowGridTitles: (Boolean) -> Unit,
    onToggleOneHandedMode: (Boolean) -> Unit,
    onSetProgressBarHeight: (Int) -> Unit,
    onSetNavBarHeight: (Int) -> Unit,
    onToggleConfirmDelete: (Boolean) -> Unit
) {
    Text(
        stringResource(R.string.settings_section_interface),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

    SettingsCard {
        // NEU: Auswahl Kacheln vs. Liste
        Text(stringResource(R.string.settings_layout_mode), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LayoutChip(
                label = stringResource(R.string.layout_grid),
                selected = layoutMode == LayoutMode.GRID,
                onClick = { onSetLayoutMode(LayoutMode.GRID) },
                icon = Icons.Default.GridView
            )
            LayoutChip(
                label = stringResource(R.string.layout_list),
                selected = layoutMode == LayoutMode.LIST,
                onClick = { onSetLayoutMode(LayoutMode.LIST) },
                icon = Icons.AutoMirrored.Filled.ViewList
            )
        }

        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

        // Slider nur anzeigen, wenn GRID aktiv ist
        if (layoutMode == LayoutMode.GRID) {
            SettingsSliderCard(
                title = stringResource(R.string.settings_tile_size),
                value = gridSize,
                valueRange = SettingsDefaults.MIN_GRID_SIZE_DP..SettingsDefaults.MAX_GRID_SIZE_DP,
                onValueChangeFinished = onSetGridSize,
                valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
            )
            Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        }

        SettingsSwitchCard(
            title = stringResource(R.string.settings_show_titles),
            checked = showGridTitles,
            onCheckedChange = onToggleShowGridTitles
        )
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_one_handed_mode),
        subtitle = stringResource(R.string.settings_one_handed_mode_subtitle),
        checked = oneHandedMode,
        onCheckedChange = onToggleOneHandedMode
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSliderCard(
        title = stringResource(R.string.settings_progress_bar_height),
        value = progressBarHeight,
        valueRange = SettingsDefaults.MIN_PROGRESS_BAR_HEIGHT_DP..SettingsDefaults.MAX_PROGRESS_BAR_HEIGHT_DP,
        onValueChangeFinished = onSetProgressBarHeight,
        valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
    )

    if (oneHandedMode) {
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        SettingsSliderCard(
            title = stringResource(R.string.settings_navigation_bar_height),
            value = navBarHeight,
            valueRange = SettingsDefaults.MIN_NAV_BAR_HEIGHT_DP..SettingsDefaults.MAX_NAV_BAR_HEIGHT_DP,
            onValueChangeFinished = onSetNavBarHeight,
            valueDisplay = { Text(stringResource(R.string.settings_unit_dp, it)) }
        )
    }

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_confirm_delete),
        subtitle = stringResource(R.string.settings_confirm_delete_subtitle),
        checked = confirmDelete,
        onCheckedChange = onToggleConfirmDelete
    )
}

@Composable
fun SectionPlayback(
    markPlayedDurationSeconds: Int,
    bufferMode: BufferMode,
    onSetMarkPlayedDuration: (Int) -> Unit,
    onSetBufferMode: (BufferMode) -> Unit
) {
    Text(
        stringResource(R.string.settings_section_playback),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

    SettingsSliderCard(
        title = stringResource(R.string.settings_mark_as_played_after),
        value = markPlayedDurationSeconds,
        valueRange = SettingsDefaults.MIN_MARK_PLAYED_DURATION_SECONDS..SettingsDefaults.MAX_MARK_PLAYED_DURATION_SECONDS,
        steps = (SettingsDefaults.MAX_MARK_PLAYED_DURATION_SECONDS - SettingsDefaults.MIN_MARK_PLAYED_DURATION_SECONDS - 1).toInt(),
        onValueChangeFinished = onSetMarkPlayedDuration,
        valueDisplay = {
            val seconds = it
            if (seconds == 0) {
                Text(stringResource(R.string.settings_mark_as_played_percentage))
            } else {
                Text(stringResource(R.string.settings_unit_seconds, seconds))
            }
        }
    )

    Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

    SettingsCard {
        Text(stringResource(R.string.settings_buffer_mode), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSetBufferMode(BufferMode.NORMAL) }
        ) {
            RadioButton(
                selected = bufferMode == BufferMode.NORMAL,
                onClick = { onSetBufferMode(BufferMode.NORMAL) }
            )
            Text(
                stringResource(R.string.settings_buffer_mode_normal),
                modifier = Modifier.padding(start = Dimens.PaddingVerySmall)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSetBufferMode(BufferMode.MAXIMAL) }
        ) {
            RadioButton(
                selected = bufferMode == BufferMode.MAXIMAL,
                onClick = { onSetBufferMode(BufferMode.MAXIMAL) }
            )
            Text(
                stringResource(R.string.settings_buffer_mode_maximal),
                modifier = Modifier.padding(start = Dimens.PaddingVerySmall)
            )
        }
    }
}

@Composable
fun SectionStatistics(
    statsState: StatisticsScreenUiState,
    onResetStatistics: () -> Unit
) {
    val context = LocalContext.current
    Text(
        stringResource(R.string.settings_section_statistics),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

    SettingsCard {
        when (statsState) {
            is StatisticsScreenUiState.Success -> {
                // Laufzeit der Statistik
                if (statsState.statisticsStartedAt > 0L) {
                    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
                    val startDate = remember(statsState.statisticsStartedAt) {
                        dateFormat.format(Date(statsState.statisticsStartedAt))
                    }
                    val daysActive = remember(statsState.statisticsStartedAt) {
                        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - statsState.statisticsStartedAt)
                    }
                    val daysLabel = if (daysActive == 1L)
                        stringResource(R.string.settings_statistics_days_singular)
                    else
                        stringResource(R.string.settings_statistics_days_plural)
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

// NEU: Helper für die Auswahl-Chips
@Composable
fun LayoutChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.PaddingMedium)
            )
        }
    )
}

@Composable
fun SectionDownloadLocation(
    saveToDownloadsFolder: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // Permission launcher only used on API < 29
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onToggle(true)
        // If denied: toggle stays off, no state change
    }

    val locationDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "Android 10+: Dateien werden in Android/data/\u2026/files/Downloads gespeichert. Kein Berechtigungsdialog n\u00f6tig."
    } else {
        "Android 9 und \u00e4lter: Dateien werden im \u00f6ffentlichen Downloads-Ordner gespeichert. Speicherzugriff wird angefragt."
    }

    Text(
        text = "Download-Speicherort",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = Dimens.PaddingTiny, bottom = Dimens.PaddingVerySmall)
    )

    SettingsCard {
        SettingsSwitchCard(
            title = "Im Download-Ordner speichern",
            subtitle = locationDescription,
            checked = saveToDownloadsFolder,
            onCheckedChange = { enabled ->
                if (!enabled) {
                    // Turning off: always allowed
                    onToggle(false)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // API 29+: no permission needed
                    onToggle(true)
                } else {
                    // API < 29: check WRITE_EXTERNAL_STORAGE
                    val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                        onToggle(true)
                    } else {
                        launcher.launch(permission)
                    }
                }
            }
        )
    }
}

// NEU: Helper für den Color Picker des Indicators (Long Farben)
@Composable
fun IndicatorColorCircle(
    colorArgb: Long,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent
    val color = Color(colorArgb)
    // FIX: Manuelle Luminanz-Berechnung statt luminance() Extension
    val luminance = (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)

    Box(
        modifier =
        Modifier
            .size(Dimens.ColorCircleSize)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, borderColor, CircleShape)
            .clickable(onClick = onClick)
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (luminance > 0.5) Color.Black else Color.White,
                modifier =
                Modifier
                    .size(Dimens.CheckIconSize)
                    .align(Alignment.Center)
            )
        }
    }
}
