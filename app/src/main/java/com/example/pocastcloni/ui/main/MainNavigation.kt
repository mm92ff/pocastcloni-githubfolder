package com.example.pocastcloni.ui.main

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.navigation.Screen
import kotlin.math.abs

internal data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val labelResId: Int
)

internal val mainBottomNavItems =
    listOf(
        BottomNavItem(Screen.Settings, Icons.Default.Settings, R.string.nav_settings),
        BottomNavItem(Screen.Home, Icons.Default.Home, R.string.nav_home),
        BottomNavItem(Screen.Downloads, Icons.Default.Download, R.string.nav_downloads),
        BottomNavItem(Screen.Search, Icons.Default.Search, R.string.nav_search)
    )

internal val MainScreenSwipeThreshold = 96.dp

private const val MainScreenSwipeHorizontalBias = 1.4f

internal fun String?.isMainBottomNavRoute(): Boolean =
    mainBottomNavItems.any { item -> item.screen.route == this }

internal fun NavHostController.navigateMainScreen(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun adjacentMainScreen(currentRoute: String?, direction: Int): Screen? {
    val currentIndex = mainBottomNavItems.indexOfFirst { item -> item.screen.route == currentRoute }
    if (currentIndex == -1) return null

    val targetIndex = (currentIndex + direction).coerceIn(0, mainBottomNavItems.lastIndex)
    if (targetIndex == currentIndex) return null

    return mainBottomNavItems[targetIndex].screen
}

internal fun Modifier.mainScreenSwipeNavigation(
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
