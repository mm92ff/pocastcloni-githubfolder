package com.example.pocastcloni.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.locale.AppLocaleController
import com.example.pocastcloni.ui.locale.SupportedAppLanguage
import com.example.pocastcloni.ui.theme.Dimens

@Composable
internal fun AppLanguageSettings() {
    val controller = remember { AppLocaleController() }
    val configuration = LocalConfiguration.current
    val currentLanguage = remember(configuration) { controller.currentSelection }

    AppLanguageSettingsContent(
        currentLanguage = currentLanguage,
        onLanguageSelected = controller::select
    )
}

@Composable
internal fun AppLanguageSettingsContent(
    currentLanguage: SupportedAppLanguage,
    onLanguageSelected: (SupportedAppLanguage) -> Unit
) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }

    SettingsCard(onClick = { showLanguageDialog = true }) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_app_language),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(currentLanguage.labelResource),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showLanguageDialog) {
        AppLanguageDialog(
            currentLanguage = currentLanguage,
            onLanguageSelected = { language ->
                showLanguageDialog = false
                onLanguageSelected(language)
            },
            onDismiss = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun AppLanguageDialog(
    currentLanguage: SupportedAppLanguage,
    onLanguageSelected: (SupportedAppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_app_language_dialog_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                SupportedAppLanguage.entries.forEach { language ->
                    val label = stringResource(language.labelResource)
                    val selected = language == currentLanguage
                    Row(
                        modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onLanguageSelected(language) }
                            )
                            .padding(vertical = Dimens.PaddingVerySmall),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Text(
                            text = label,
                            modifier = Modifier.padding(start = Dimens.PaddingVerySmall)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

private val SupportedAppLanguage.labelResource: Int
    get() =
        when (this) {
            SupportedAppLanguage.SYSTEM_DEFAULT -> R.string.settings_language_system_default
            SupportedAppLanguage.ENGLISH -> R.string.settings_language_english
            SupportedAppLanguage.GERMAN -> R.string.settings_language_german
        }
