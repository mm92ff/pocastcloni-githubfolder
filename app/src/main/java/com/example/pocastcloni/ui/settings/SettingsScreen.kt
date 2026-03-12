package com.example.pocastcloni.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.UiText
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: SettingsBackupViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsViewModel.SettingsUiEffect.Snackbar -> {
                    snackbarHostState.showSnackbar(effect.message.asString(context))
                }
            }
        }
    }

    HandleBackupSideEffects(backupState, snackbarHostState, context)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Constants.Backup.MIME_TYPE_JSON)
    ) { uri ->
        uri?.let { backupViewModel.onEvent(SettingsUiEvent.ExportFullBackup(it.toString())) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { backupViewModel.onEvent(SettingsUiEvent.ImportFullBackup(it.toString())) }
    }

    val onExportClick = remember { { exportLauncher.launch(Constants.Backup.BACKUP_FILE_NAME) } }
    val onImportClick = remember { { importLauncher.launch(arrayOf(Constants.Backup.MIME_TYPE_JSON, Constants.Backup.MIME_TYPE_ALL)) } }

    HandleImportState(
        state = backupState.importState,
        onReset = { backupViewModel.onEvent(SettingsUiEvent.ResetImportState) }
    )
    HandleExportState(
        state = backupState.exportState,
        onReset = { backupViewModel.onEvent(SettingsUiEvent.ResetExportState) }
    )

    if (state.showResetDialog) {
        ResetAppDialog(
            onDismiss = { viewModel.onEvent(SettingsUiEvent.OnResetDismissed) },
            onConfirm = { viewModel.onEvent(SettingsUiEvent.OnResetConfirmed) }
        )
    }

    if (state.isManualRefreshRunning) {
        ManualRefreshDialog()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val settings = state.settings) {
                is SettingsUiState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                is SettingsUiState.Error -> {
                    Text(settings.message.asString(context), Modifier.align(Alignment.Center))
                }
                is SettingsUiState.Success -> {
                    SettingsListContent(
                        settings = settings,
                        downloadMessage = null,
                        isPlayerVisible = state.isPlayerVisible,
                        onEvent = viewModel::onEvent,
                        onExportClick = onExportClick,
                        onImportClick = onImportClick
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualRefreshDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(shape = RoundedCornerShape(Dimens.PaddingMedium)) {
            Column(
                modifier = Modifier.padding(Dimens.PaddingLarge),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                Text(stringResource(R.string.updating_all_feeds))
            }
        }
    }
}

@Composable
private fun HandleBackupSideEffects(
    backupState: SettingsBackupViewModel.BackupUiState,
    snackbarHostState: SnackbarHostState,
    context: android.content.Context
) {
    LaunchedEffect(backupState.importState) {
        if (backupState.importState is ImportUiState.Success) {
            snackbarHostState.showSnackbar((backupState.importState as ImportUiState.Success).message.asString(context))
        }
    }
    LaunchedEffect(backupState.exportState) {
        if (backupState.exportState is ExportUiState.Success) {
            snackbarHostState.showSnackbar((backupState.exportState as ExportUiState.Success).message.asString(context))
        }
    }
}

@Composable
private fun ResetAppDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text(stringResource(R.string.settings_reset_dialog_title)) },
        text = { Text(stringResource(R.string.settings_reset_dialog_message)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.settings_reset_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun HandleImportState(state: ImportUiState, onReset: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        is ImportUiState.Loading -> {
            Dialog(onDismissRequest = {}) {
                Card(shape = RoundedCornerShape(Dimens.PaddingMedium)) {
                    Column(
                        modifier = Modifier.padding(Dimens.PaddingLarge),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                        Text(stringResource(R.string.importing_backup))
                    }
                }
            }
        }
        is ImportUiState.Success -> {
            AlertDialog(
                onDismissRequest = onReset,
                confirmButton = { TextButton(onClick = onReset) { Text(stringResource(R.string.ok)) } },
                title = { Text(stringResource(R.string.import_complete)) },
                text = { Text(state.message.asString(context)) }
            )
        }
        is ImportUiState.Error -> {
            AlertDialog(
                onDismissRequest = onReset,
                confirmButton = { TextButton(onClick = onReset) { Text(stringResource(R.string.ok)) } },
                title = { Text(stringResource(R.string.error)) },
                text = { Text(state.message.asString(context)) }
            )
        }
        else -> {}
    }
}

@Composable
fun HandleExportState(state: ExportUiState, onReset: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        is ExportUiState.Loading -> {
            Dialog(onDismissRequest = {}) {
                Card(shape = RoundedCornerShape(Dimens.PaddingMedium)) {
                    Column(
                        modifier = Modifier.padding(Dimens.PaddingLarge),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                        Text(stringResource(R.string.exporting_backup))
                    }
                }
            }
        }
        is ExportUiState.Success -> {
            AlertDialog(
                onDismissRequest = onReset,
                confirmButton = { TextButton(onClick = onReset) { Text(stringResource(R.string.ok)) } },
                title = { Text(stringResource(R.string.export_complete)) },
                text = { Text(state.message.asString(context)) }
            )
        }
        is ExportUiState.Error -> {
            AlertDialog(
                onDismissRequest = onReset,
                confirmButton = { TextButton(onClick = onReset) { Text(stringResource(R.string.ok)) } },
                title = { Text(stringResource(R.string.error)) },
                text = { Text(state.message.asString(context)) }
            )
        }
        else -> {}
    }
}