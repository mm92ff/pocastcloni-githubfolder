package com.example.pocastcloni.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.pocastcloni.R
import com.example.pocastcloni.domain.repository.UserSettings
import com.example.pocastcloni.ui.navigation.Screen
import com.example.pocastcloni.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.abs

private val BottomBarHandleHeight = 48.dp
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
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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

    AnimatedVisibility(
        visible = bottomBarVisible,
        enter =
        slideInVertically(animationSpec = Motion.enterSpec()) { height -> height } +
            fadeIn(animationSpec = Motion.enterSpec()),
        exit =
        slideOutVertically(animationSpec = Motion.exitSpec()) { height -> height } +
            fadeOut(animationSpec = Motion.exitSpec())
    ) {
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
    }

    AnimatedVisibility(
        visible = !bottomBarVisible,
        enter =
        slideInVertically(animationSpec = Motion.enterSpec()) { height -> height / 2 } +
            fadeIn(animationSpec = Motion.enterSpec()),
        exit =
        slideOutVertically(animationSpec = Motion.exitSpec()) { height -> height / 2 } +
            fadeOut(animationSpec = Motion.exitSpec())
    ) {
        BottomBarRevealHandle(
            onReveal = {
                bottomBarRevealed = true
                autoHideTimerKey++
            },
            bottomInset = navigationBarInset,
            modifier =
            Modifier
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
internal fun BottomBarRevealHandle(
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp
) {
    val label = stringResource(R.string.desc_reveal_bottom_bar)
    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .height(BottomBarHandleHeight + bottomInset)
            .padding(bottom = bottomInset)
            .clickable(
                role = Role.Button,
                onClickLabel = label,
                onClick = onReveal
            )
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                onClick(label = label) {
                    onReveal()
                    true
                }
            },
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
    val transparentBottomBar = userSettings.transparentBottomBar
    val navigationBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val transparentItemColors =
        NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onBackground,
            selectedTextColor = MaterialTheme.colorScheme.onBackground,
            indicatorColor = Color.Transparent,
            unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
        )

    NavigationBar(
        modifier = modifier.height(userSettings.navBarHeight.dp + navigationBarInset),
        containerColor = if (transparentBottomBar) Color.Transparent else NavigationBarDefaults.containerColor,
        tonalElevation = if (transparentBottomBar) 0.dp else NavigationBarDefaults.Elevation
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
                colors =
                if (transparentBottomBar) {
                    transparentItemColors
                } else {
                    NavigationBarItemDefaults.colors()
                },
                onClick = {
                    if (item.screen == Screen.Home) {
                        if (!isSelected) {
                            navController.navigateMainScreen(Screen.Home)
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
