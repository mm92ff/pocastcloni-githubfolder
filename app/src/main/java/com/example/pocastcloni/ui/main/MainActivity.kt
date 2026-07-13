package com.example.pocastcloni.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.favorites.FavoritesScreen
import com.example.pocastcloni.ui.history.HistoryScreen
import com.example.pocastcloni.ui.home.add.AddPodcastScreen
import com.example.pocastcloni.ui.home.detail.PodcastDetailScreen
import com.example.pocastcloni.ui.home.downloads.DownloadsScreen
import com.example.pocastcloni.ui.home.feed.HomeScreen
import com.example.pocastcloni.ui.navigation.Screen
import com.example.pocastcloni.ui.player.PlayerContainer
import com.example.pocastcloni.ui.settings.SettingsScreen
import com.example.pocastcloni.ui.theme.PocastCloniTheme
import com.example.pocastcloni.ui.theme.isPocastCloniDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val navController = rememberNavController()

            // Stable callback: no Compose-state capture; back-stack is read on demand
            val onNavigateToPodcastDetail: (String) -> Unit =
                remember(navController) {
                    { url ->
                        val entry = navController.currentBackStackEntry
                        val currentRoute = entry?.destination?.route

                        if (currentRoute != Screen.PodcastDetail.route) {
                            navController.navigate(Screen.PodcastDetail.createRoute(url))
                        } else {
                            val currentPodcastUrl = entry.arguments?.getString(Screen.PODCAST_URL)
                            val decodedUrl =
                                currentPodcastUrl?.let {
                                    URLDecoder.decode(it, StandardCharsets.UTF_8.name())
                                }
                            if (decodedUrl != url) {
                                navController.navigate(Screen.PodcastDetail.createRoute(url))
                            } else {
                                navController.navigateMainScreen(Screen.Home)
                            }
                        }
                    }
                }

            PocastCloniTheme(
                appTheme = uiState.userSettings.theme,
                appColor = uiState.userSettings.appColor,
                colorStrength = uiState.userSettings.colorStrength,
                gradientBackgroundEnabled = uiState.userSettings.gradientBackgroundEnabled,
                gradientBackgroundStrength = uiState.userSettings.gradientBackgroundStrength,
                gradientBackgroundDirection = uiState.userSettings.gradientBackgroundDirection
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    else -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AppGradientBackground(
                                userSettings = uiState.userSettings,
                                darkTheme = isPocastCloniDarkTheme(uiState.userSettings.theme)
                            ) {
                                Scaffold(
                                    containerColor = Color.Transparent,
                                    bottomBar = {
                                        CleanModeBottomBarHost(
                                            navController = navController,
                                            userSettings = uiState.userSettings,
                                            onPlayerExpanded = { viewModel.onPlayerExpanded(it) }
                                        )
                                    }
                                ) { innerPadding ->
                                    Box(
                                        modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(innerPadding)
                                    ) {
                                        val currentBackStackEntry by navController.currentBackStackEntryAsState()
                                        val currentRoute = currentBackStackEntry?.destination?.route
                                        val suppressMiniPlayer = currentRoute == Screen.Search.route
                                        val mainScreenSwipeThresholdPx =
                                            with(LocalDensity.current) { MainScreenSwipeThreshold.toPx() }

                                        NavHost(
                                            navController = navController,
                                            startDestination = Screen.Home.route,
                                            enterTransition = { mainScreenEnterTransition() },
                                            exitTransition = { mainScreenExitTransition() },
                                            popEnterTransition = { mainScreenEnterTransition() },
                                            popExitTransition = { mainScreenExitTransition() },
                                            modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .mainScreenSwipeNavigation(
                                                    enabled = currentRoute.isMainBottomNavRoute(),
                                                    currentRoute = currentRoute,
                                                    thresholdPx = mainScreenSwipeThresholdPx,
                                                    onNavigate = { screen ->
                                                        navController.navigateMainScreen(screen)
                                                    }
                                                )
                                        ) {
                                            composable(Screen.Home.route) {
                                                HomeScreen(
                                                    onPodcastClicked = { url ->
                                                        navController.navigate(Screen.PodcastDetail.createRoute(url))
                                                    },
                                                    onFavoritesClicked = {
                                                        navController.navigate(Screen.Favorites.route)
                                                    },
                                                    onHistoryClicked = {
                                                        navController.navigate(Screen.History.route)
                                                    }
                                                )
                                            }
                                            composable(Screen.Search.route) {
                                                AddPodcastScreen(
                                                    onNavigateBack = { navController.popBackStack() },
                                                    reserveSpaceForPlayer = false
                                                )
                                            }
                                            composable(Screen.Downloads.route) { DownloadsScreen() }
                                            composable(Screen.Settings.route) {
                                                SettingsScreen(onNavigateBack = { navController.popBackStack() })
                                            }
                                            composable(Screen.Favorites.route) {
                                                FavoritesScreen(onNavigateBack = { navController.popBackStack() })
                                            }
                                            composable(Screen.History.route) {
                                                HistoryScreen(onNavigateBack = { navController.popBackStack() })
                                            }
                                            composable(Screen.PodcastDetail.route) {
                                                PodcastDetailScreen(onNavigateBack = { navController.popBackStack() })
                                            }
                                        }

                                        PlayerContainer(
                                            userSettings = uiState.userSettings,
                                            progressBarHeight = uiState.userSettings.progressBarHeight.dp,
                                            navBarHeight = uiState.userSettings.navBarHeight.dp,
                                            showMiniPlayerTimeOverlay = uiState.userSettings.showMiniPlayerTimeOverlay,
                                            transparentMiniPlayer = uiState.userSettings.transparentMiniPlayer,
                                            isExpanded = uiState.isPlayerExpanded,
                                            onExpandedChange = viewModel::onPlayerExpanded,
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                            onNavigateToPodcastDetail = onNavigateToPodcastDetail,
                                            suppress = suppressMiniPlayer
                                        )
                                    }
                                }
                            }
                            uiState.error?.let { error ->
                                MainSettingsErrorBanner(
                                    message = error.asString(),
                                    onRetry = viewModel::retrySettings,
                                    modifier =
                                    Modifier
                                        .align(Alignment.TopCenter)
                                        .systemBarsPadding()
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
// This UI-emitting composable follows Compose's PascalCase naming convention.
@Suppress("FunctionNaming")
internal fun MainSettingsErrorBanner(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = message, modifier = Modifier.weight(1f))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
