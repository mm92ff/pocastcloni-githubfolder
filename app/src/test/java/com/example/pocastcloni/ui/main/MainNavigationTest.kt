package com.example.pocastcloni.ui.main

import com.example.pocastcloni.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainNavigationTest {
    @Test
    fun `main bottom nav route detection only accepts main screens`() {
        assertTrue(Screen.Home.route.isMainBottomNavRoute())
        assertTrue(Screen.Search.route.isMainBottomNavRoute())
        assertNull(adjacentMainScreen(Screen.PodcastDetail.route, direction = 1))
    }

    @Test
    fun `adjacentMainScreen follows visible bottom bar order`() {
        assertEquals(Screen.Home, adjacentMainScreen(Screen.Settings.route, direction = 1))
        assertEquals(Screen.Downloads, adjacentMainScreen(Screen.Home.route, direction = 1))
        assertEquals(Screen.Home, adjacentMainScreen(Screen.Downloads.route, direction = -1))
        assertEquals(Screen.Downloads, adjacentMainScreen(Screen.Search.route, direction = -1))
    }

    @Test
    fun `adjacentMainScreen stops at the first and last main screen`() {
        assertNull(adjacentMainScreen(Screen.Settings.route, direction = -1))
        assertNull(adjacentMainScreen(Screen.Search.route, direction = 1))
        assertNull(adjacentMainScreen(null, direction = 1))
    }
}
