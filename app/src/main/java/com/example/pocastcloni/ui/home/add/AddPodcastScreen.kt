package com.example.pocastcloni.ui.home.add

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Dimens
import com.example.pocastcloni.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPodcastScreen(
    viewModel: AddPodcastViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    reserveSpaceForPlayer: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_add_podcast)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->

        val bottomPlayerPadding =
            remember(
                reserveSpaceForPlayer,
                uiState.isPlayerVisible,
                uiState.navBarHeight,
                uiState.progressBarHeight
            ) {
                if (reserveSpaceForPlayer && uiState.isPlayerVisible) {
                    (uiState.navBarHeight + uiState.progressBarHeight).dp + Dimens.PaddingMedium
                } else {
                    Dimens.Zero
                }
            }

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // vorher: PaddingLarge -> kompakter
                .padding(horizontal = Dimens.PaddingMedium)
                .padding(bottom = bottomPlayerPadding)
                .imePadding()
        ) {
            if (uiState.oneHandedMode) {
                Box(modifier = Modifier.weight(Constants.Weights.FULL)) {
                    SearchResultsList(
                        results = uiState.searchResults,
                        subscribedUrls = uiState.subscribedUrls,
                        transparentCards = uiState.transparentSearchCards,
                        onToggleClick = viewModel::onTogglePodcast,
                        reverseLayout = true
                    )
                }
                // vorher: PaddingLarge
                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                SearchSection(uiState, viewModel, keyboardController)
            } else {
                // vorher: PaddingLarge
                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                SearchSection(uiState, viewModel, keyboardController)
                // vorher: PaddingLarge
                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))

                Box(modifier = Modifier.weight(Constants.Weights.FULL)) {
                    SearchResultsList(
                        results = uiState.searchResults,
                        subscribedUrls = uiState.subscribedUrls,
                        transparentCards = uiState.transparentSearchCards,
                        onToggleClick = viewModel::onTogglePodcast,
                        reverseLayout = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSection(
    uiState: AddPodcastScreenUiState,
    viewModel: AddPodcastViewModel,
    keyboardController: SoftwareKeyboardController?
) {
    SearchArea(
        searchQuery = uiState.searchQuery,
        onSearchQueryChange = viewModel::onSearchQueryChanged,
        isSearching = uiState.isSearching,
        onSearchTriggered = viewModel::onSearchTriggered,
        searchError = uiState.searchError?.asString(),
        keyboardController = keyboardController
    )
}
