package com.example.pocastcloni.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlin.math.abs

private val BottomBarHandleHeight = 18.dp
private val BottomBarHandleWidth = 44.dp
private val BottomBarHandleThickness = 4.dp
private val BottomBarSwipeThreshold = 48.dp

@Composable
internal fun CleanModeBottomBarHost(
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

        mainBottomNavItems.forEach { item ->
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
