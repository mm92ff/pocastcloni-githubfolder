package com.example.pocastcloni.ui.main

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.repository.UserSettings
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
import com.example.pocastcloni.ui.theme.gradientBackgroundBottomColor
import com.example.pocastcloni.ui.theme.isPocastCloniDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

private data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val labelResId: Int
)

private val bottomNavItems =
    listOf(
        BottomNavItem(Screen.Settings, Icons.Default.Settings, R.string.nav_settings),
        BottomNavItem(Screen.Home, Icons.Default.Home, R.string.nav_home),
        BottomNavItem(Screen.Downloads, Icons.Default.Download, R.string.nav_downloads),
        BottomNavItem(Screen.Search, Icons.Default.Search, R.string.nav_search)
    )

private val BottomBarHandleHeight = 18.dp
private val BottomBarHandleWidth = 44.dp
private val BottomBarHandleThickness = 4.dp
private val BottomBarSwipeThreshold = 48.dp
private val MainScreenSwipeThreshold = 96.dp
private const val MainScreenSwipeHorizontalBias = 1.4f

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
                                val returnedHome = navController.popBackStack(Screen.Home.route, false)
                                if (!returnedHome) {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                    }
                }

            PocastCloniTheme(
                appTheme = uiState.userSettings.theme,
                appColor = uiState.userSettings.appColor,
                colorStrength = uiState.userSettings.colorStrength,
                gradientBackgroundEnabled = uiState.userSettings.gradientBackgroundEnabled,
                gradientBackgroundStrength = uiState.userSettings.gradientBackgroundStrength
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

                    uiState.error != null -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = uiState.error ?: stringResource(R.string.error_unknown))
                        }
                    }

                    else -> {
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
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                        onNavigateToPodcastDetail = onNavigateToPodcastDetail,
                                        suppress = suppressMiniPlayer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppGradientBackground(
    userSettings: UserSettings,
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val backgroundModifier =
        if (userSettings.gradientBackgroundEnabled) {
            Modifier.background(
                Brush.verticalGradient(
                    colors =
                    listOf(
                        if (darkTheme) Color.Black else Color.White,
                        gradientBackgroundBottomColor(
                            appColor = userSettings.appColor,
                            darkTheme = darkTheme,
                            strength = userSettings.gradientBackgroundStrength
                        )
                    )
                )
            )
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }

    Box(
        modifier =
        Modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        content()
    }
}

private fun String?.isMainBottomNavRoute(): Boolean =
    bottomNavItems.any { item -> item.screen.route == this }

private fun NavHostController.navigateMainScreen(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun adjacentMainScreen(currentRoute: String?, direction: Int): Screen? {
    val currentIndex = bottomNavItems.indexOfFirst { item -> item.screen.route == currentRoute }
    if (currentIndex == -1) return null

    val targetIndex = (currentIndex + direction).coerceIn(0, bottomNavItems.lastIndex)
    if (targetIndex == currentIndex) return null

    return bottomNavItems[targetIndex].screen
}

private fun Modifier.mainScreenSwipeNavigation(
    enabled: Boolean,
    currentRoute: String?,
    thresholdPx: Float,
    onNavigate: (Screen) -> Unit
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled, currentRoute, thresholdPx, onNavigate) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var activePointerId = down.id
            var totalX = 0f
            var totalY = 0f

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change =
                    event.changes.firstOrNull { pointerChange -> pointerChange.id == activePointerId }
                        ?: event.changes.firstOrNull()
                        ?: break

                activePointerId = change.id

                if (change.changedToUpIgnoreConsumed()) break

                val delta = change.positionChangeIgnoreConsumed()
                totalX += delta.x
                totalY += delta.y

                val isClearHorizontalSwipe =
                    abs(totalX) >= thresholdPx &&
                        abs(totalX) > abs(totalY) * MainScreenSwipeHorizontalBias

                if (isClearHorizontalSwipe) {
                    val direction = if (totalX < 0f) 1 else -1
                    adjacentMainScreen(currentRoute, direction)?.let(onNavigate)
                    break
                }
            }
        }
    }
}

@Composable
private fun CleanModeBottomBarHost(
    navController: NavHostController,
    userSettings: UserSettings,
    onPlayerExpanded: (Boolean) -> Unit
) {
    val cleanModeEnabled = userSettings.bottomBarCleanModeEnabled
    var bottomBarRevealed by rememberSaveable { mutableStateOf(false) }
    var autoHideTimerKey by rememberSaveable { mutableStateOf(0) }
    val swipeThresholdPx = with(LocalDensity.current) { BottomBarSwipeThreshold.toPx() }
    val autoHideDelayMillis = userSettings.bottomBarAutoHideDelaySeconds.coerceAtLeast(1) * 1_000L

    LaunchedEffect(cleanModeEnabled) {
        bottomBarRevealed = !cleanModeEnabled
        autoHideTimerKey++
    }

    val bottomBarVisible = !cleanModeEnabled || bottomBarRevealed

    LaunchedEffect(
        cleanModeEnabled,
        bottomBarRevealed,
        userSettings.bottomBarAutoHideEnabled,
        autoHideTimerKey,
        autoHideDelayMillis
    ) {
        if (cleanModeEnabled && bottomBarRevealed && userSettings.bottomBarAutoHideEnabled) {
            delay(autoHideDelayMillis)
            bottomBarRevealed = false
        }
    }

    if (bottomBarVisible) {
        AppBottomNavigation(
            navController = navController,
            userSettings = userSettings,
            onPlayerExpanded = onPlayerExpanded,
            onNavigationItemClicked = { screen ->
                if (cleanModeEnabled && userSettings.bottomBarAutoHideEnabled) {
                    autoHideTimerKey++
                } else if (cleanModeEnabled && screen != Screen.Settings) {
                    bottomBarRevealed = false
                }
            },
            modifier =
            Modifier.bottomBarSwipeGesture(
                enabled = cleanModeEnabled,
                thresholdPx = swipeThresholdPx,
                onSwipeDown = { bottomBarRevealed = false }
            )
        )
    } else {
        BottomBarRevealHandle(
            modifier =
            Modifier
                .height(BottomBarHandleHeight)
                .bottomBarSwipeGesture(
                    enabled = cleanModeEnabled,
                    thresholdPx = swipeThresholdPx,
                    onSwipeUp = {
                        bottomBarRevealed = true
                        autoHideTimerKey++
                    }
                )
        )
    }
}

@Composable
private fun BottomBarRevealHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier =
            Modifier
                .width(BottomBarHandleWidth)
                .height(BottomBarHandleThickness)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        )
    }
}

private fun Modifier.bottomBarSwipeGesture(
    enabled: Boolean,
    thresholdPx: Float,
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null
): Modifier {
    if (!enabled) return this

    return pointerInput(enabled, thresholdPx, onSwipeUp, onSwipeDown) {
        var totalX = 0f
        var totalY = 0f

        detectDragGestures(
            onDragStart = {
                totalX = 0f
                totalY = 0f
            },
            onDragEnd = {
                val isVerticalIntent = abs(totalY) > abs(totalX)
                when {
                    isVerticalIntent && totalY <= -thresholdPx -> onSwipeUp?.invoke()
                    isVerticalIntent && totalY >= thresholdPx -> onSwipeDown?.invoke()
                }
            },
            onDragCancel = {
                totalX = 0f
                totalY = 0f
            },
            onDrag = { change, dragAmount ->
                totalX += dragAmount.x
                totalY += dragAmount.y
                change.consume()
            }
        )
    }
}

@Composable
private fun AppBottomNavigation(
    navController: NavHostController,
    userSettings: UserSettings,
    onPlayerExpanded: (Boolean) -> Unit,
    onNavigationItemClicked: (Screen) -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.height(userSettings.navBarHeight.dp)
    ) {
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = currentBackStackEntry?.destination

        LaunchedEffect(currentBackStackEntry) {
            onPlayerExpanded(false)
        }

        bottomNavItems.forEach { item ->
            val isSelected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true

            NavigationBarItem(
                icon = { Icon(item.icon, stringResource(id = item.labelResId)) },
                label = { Text(stringResource(id = item.labelResId)) },
                selected = isSelected,
                onClick = {
                    if (item.screen == Screen.Home) {
                        when (currentDestination?.route) {
                            Screen.Favorites.route,
                            Screen.History.route,
                            Screen.PodcastDetail.route
                            -> {
                                navController.popBackStack()
                            }
                            else -> {
                                if (!isSelected) {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                        onNavigationItemClicked(item.screen)
                    } else {
                        if (!isSelected) {
                            navController.navigate(item.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        onNavigationItemClicked(item.screen)
                    }
                }
            )
        }
    }
}
