package com.example.pocastcloni.ui.settings

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants.SettingsDefaults

@Composable
fun SectionDownloads(
    autoDownloadLimit: Int,
    message: UiText?,
    onSetAutoDownloadLimit: (Int) -> Unit,
    onStartManualDownload: () -> Unit
) {
    val context = LocalContext.current
    SettingsSectionTitle(stringResource(R.string.settings_section_downloads_data))

    SettingsSliderCard(
        title = stringResource(R.string.settings_auto_download_limit),
        value = autoDownloadLimit,
        valueRange = SettingsDefaults.MIN_AUTO_DOWNLOAD_LIMIT..SettingsDefaults.MAX_AUTO_DOWNLOAD_LIMIT,
        steps = (SettingsDefaults.MAX_AUTO_DOWNLOAD_LIMIT - SettingsDefaults.MIN_AUTO_DOWNLOAD_LIMIT - 1).toInt(),
        onValueChangeFinished = onSetAutoDownloadLimit,
        valueDisplay = {
            SliderValueText(text = stringResource(R.string.settings_unit_episodes, it))
        }
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
fun SectionDownloadLocation(
    saveToDownloadsFolder: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) onToggle(true)
    }

    val locationDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "Android 10+: Files are saved to Android/data/\u2026/files/Downloads. No permission dialog required."
    } else {
        "Android 9 and below: Files are saved to the public Downloads folder. Storage access will be requested."
    }

    SettingsSectionTitle("Download Location")

    SettingsSwitchCard(
        title = "Save to Downloads folder",
        subtitle = locationDescription,
        checked = saveToDownloadsFolder,
        onCheckedChange = { enabled ->
            if (!enabled) {
                onToggle(false)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                onToggle(true)
            } else {
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

@Composable
fun SectionCleanup(
    autoCleanupEnabled: Boolean,
    cleanupKeepLimit: Int,
    cleanupIntervalHours: Int,
    onToggleAutoCleanup: (Boolean) -> Unit,
    onSetCleanupKeepLimit: (Int) -> Unit,
    onSetCleanupIntervalHours: (Int) -> Unit
) {
    SettingsSectionTitle(stringResource(R.string.settings_section_cleanup))

    SettingsSwitchCard(
        title = stringResource(R.string.settings_cleanup_enabled),
        subtitle = stringResource(R.string.settings_cleanup_enabled_subtitle),
        checked = autoCleanupEnabled,
        onCheckedChange = onToggleAutoCleanup
    )

    if (autoCleanupEnabled) {
        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        SettingsSliderCard(
            title = stringResource(R.string.settings_cleanup_keep_limit),
            value = cleanupKeepLimit,
            valueRange = SettingsDefaults.MIN_CLEANUP_KEEP_LIMIT..SettingsDefaults.MAX_CLEANUP_KEEP_LIMIT,
            onValueChangeFinished = onSetCleanupKeepLimit,
            valueDisplay = {
                SliderValueText(text = stringResource(R.string.settings_unit_episodes, it))
            }
        )

        Spacer(modifier = Modifier.height(Dimens.PaddingVerySmall))
        SettingsSliderCard(
            title = stringResource(R.string.settings_cleanup_interval),
            value = cleanupIntervalHours,
            valueRange = SettingsDefaults.MIN_CLEANUP_INTERVAL_HOURS..SettingsDefaults.MAX_CLEANUP_INTERVAL_HOURS,
            onValueChangeFinished = onSetCleanupIntervalHours,
            valueDisplay = { Text(stringResource(R.string.settings_unit_hours, it)) }
        )
    }
}

@Composable
private fun SliderValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth()
    )
}
