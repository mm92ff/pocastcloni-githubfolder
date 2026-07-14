package com.example.pocastcloni.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens

@Composable
internal fun DataSettingsContent(
    onEvent: (SettingsUiEvent) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    SettingsStatisticsSectionSmart()
    SettingsTabDivider()

    SectionBackup(onExport = onExportClick, onImport = onImportClick)
    SettingsTabDivider()

    SettingsCard(onClick = { onEvent(SettingsUiEvent.OnResetClicked) }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(Dimens.PaddingMedium))
            Column {
                Text(stringResource(R.string.settings_reset_zone_title), color = MaterialTheme.colorScheme.error)
                Text(
                    stringResource(R.string.settings_reset_zone_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsStatisticsSectionSmart(viewModel: SettingsStatisticsViewModel = hiltViewModel()) {
    val state by viewModel.statsState.collectAsStateWithLifecycle()
    SectionStatistics(
        statsState = state,
        onResetStatistics = viewModel::onResetStatistics
    )
}
