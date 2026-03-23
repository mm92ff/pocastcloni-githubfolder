package com.example.pocastcloni.ui.navigation

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String) {
    companion object {
        const val PODCAST_URL = "podcastUrl"
    }

    data object Home : Screen("home")

    data object Downloads : Screen("downloads")

    data object Search : Screen("search")

    data object Settings : Screen("settings")

    data object Favorites : Screen("favorites")

    data object History : Screen("history")

    data object PodcastDetail : Screen("podcast/{$PODCAST_URL}") {
        fun createRoute(podcastUrl: String): String {
            val encodedUrl = URLEncoder.encode(podcastUrl, StandardCharsets.UTF_8.name())
            return "podcast/$encodedUrl"
        }
    }
}
