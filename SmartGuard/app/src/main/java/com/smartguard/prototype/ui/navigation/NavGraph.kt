package com.smartguard.prototype.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.smartguard.prototype.ui.screens.*

@Composable
fun SmartGuardNavGraph(navController: NavHostController) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(navController = navController)
        }

        composable(Screen.FirstRunSetup.route) {
            FirstRunSetupScreen(navController = navController)
        }

        composable(Screen.Unlock.route) {
            UnlockScreen(navController = navController)
        }

        composable(Screen.SafeMode.route) {
            SafeModeScreen(navController = navController)
        }

        composable(Screen.AdultHome.route) {
            AdultHomeScreen(navController = navController)
        }

        composable(Screen.ChildHome.route) {
            ChildHomeScreen(navController = navController)
        }

        composable(
            route = Screen.Enrollment.route,
            arguments = listOf(
                navArgument("profileId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId")
                .takeIf { !it.isNullOrBlank() }
            EnrollmentScreen(navController = navController, editProfileId = profileId)
        }

        composable(Screen.ProfileList.route) {
            ProfileListScreen(navController = navController)
        }

        composable(
            route = Screen.ProfileDetail.route,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            ProfileDetailScreen(navController = navController, profileId = profileId)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        composable(Screen.DebugPanel.route) {
            DebugPanelScreen(navController = navController)
        }

        composable(
            route = Screen.AllowedApps.route,
            arguments = listOf(navArgument("profileId") { type = NavType.StringType })
        ) { backStackEntry ->
            val profileId = backStackEntry.arguments?.getString("profileId") ?: ""
            AllowedAppsScreen(navController = navController, profileId = profileId)
        }
    }
}
