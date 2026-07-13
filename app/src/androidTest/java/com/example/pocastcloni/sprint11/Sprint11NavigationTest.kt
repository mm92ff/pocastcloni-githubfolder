package com.example.pocastcloni.sprint11

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.ui.main.navigateMainScreen
import com.example.pocastcloni.ui.navigation.Screen
import com.example.pocastcloni.ui.player.PlayerBackHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Sprint11NavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBack_collapsesPlayerWithoutChangingRoute() {
        var expanded by mutableStateOf(true)
        val route = "podcast/detail"
        composeRule.setContent {
            PlayerBackHandler(isExpanded = expanded, onCollapse = { expanded = false })
            Text(route)
        }

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }

        composeRule.onNodeWithText(route).assertIsDisplayed()
        composeRule.runOnIdle { assertFalse(expanded) }
    }

    @Test
    fun mainHomeNavigation_clearsNestedDetailStack() {
        lateinit var navController: NavHostController
        composeRule.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = Screen.Home.route) {
                composable(Screen.Home.route) { Text("Home route") }
                composable("detail") { Text("Detail route") }
                composable("detail/nested") { Text("Nested detail route") }
            }
        }

        composeRule.runOnIdle {
            navController.navigate("detail")
            navController.navigate("detail/nested")
        }
        composeRule.onNodeWithText("Nested detail route").assertIsDisplayed()

        composeRule.runOnIdle { navController.navigateMainScreen(Screen.Home) }

        composeRule.onNodeWithText("Home route").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(Screen.Home.route, navController.currentDestination?.route)
            assertFalse(navController.popBackStack())
        }
    }
}

