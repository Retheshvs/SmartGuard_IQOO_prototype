package com.smartguard.prototype.ui.navigation

sealed class Screen(val route: String) {
    object Splash        : Screen("splash")
    object FirstRunSetup : Screen("first_run_setup")
    object Unlock        : Screen("unlock")
    object SafeMode      : Screen("safe_mode")
    object AdultHome     : Screen("adult_home")
    object ChildHome     : Screen("child_home")
    object Enrollment    : Screen("enrollment?profileId={profileId}") {
        fun createRoute(profileId: String? = null): String =
            if (!profileId.isNullOrBlank()) "enrollment?profileId=$profileId"
            else "enrollment?profileId="
    }
    object ProfileList   : Screen("profile_list")
    object ProfileDetail : Screen("profile_detail/{profileId}") {
        fun createRoute(profileId: String): String = "profile_detail/$profileId"
    }
    object Settings      : Screen("settings")
    object DebugPanel    : Screen("debug_panel")
    object AllowedApps   : Screen("allowed_apps/{profileId}") {
        fun createRoute(profileId: String): String = "allowed_apps/$profileId"
    }
}
