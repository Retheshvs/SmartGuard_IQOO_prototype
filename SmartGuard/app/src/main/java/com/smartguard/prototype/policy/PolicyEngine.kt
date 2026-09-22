package com.smartguard.prototype.policy

import android.content.Context
import android.util.Log
import com.smartguard.prototype.profile.AgeCalculator
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.Role
import com.smartguard.prototype.session.AppMode
import com.smartguard.prototype.session.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A dummy app tile shown in the home screen grids. */
data class AppTile(
    val id: String,
    val name: String,
    val emoji: String,
    val category: String,
    val isRestricted: Boolean
)

/**
 * Evaluates whether a given package is allowed or blocked for the currently active profile.
 *
 * Called by [SmartGuardAccessibilityService] on every foreground-app change.
 * All decisions are synchronous (no suspend) so the AccessibilityService can
 * call this on its main thread without risk.
 */
@Singleton
class PolicyEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val ageCalculator: AgeCalculator
) {

    companion object {
        private const val TAG = "PolicyEngine"

        /** Mapping of AppTile IDs to real Android package names for demo purposes. */
        private val DEMO_PACKAGE_MAPPING = mapOf(
            "youtube"   to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "tiktok"    to "com.zhiliaoapp.musically",
            "twitter"   to "com.twitter.android",
            "facebook"  to "com.facebook.katana",
            "snapchat"  to "com.snapchat.android",
            "playstore" to "com.android.vending"
        )

        /**
         * System packages — always exempt.
         * Blocking these would make the device unusable.
         */
        private val SYSTEM_PACKAGES = setOf(
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",          // Samsung
            "com.miui.home",                         // Xiaomi
            "com.oneplus.launcher",
            "com.android.systemui",
            "com.android.settings",                  // Keep settings reachable
            
            // Keyboards / Input Methods (Crucial)
            "com.google.android.inputmethod.latin",  // Gboard
            "com.samsung.android.honeyboard",        // Samsung Keyboard
            "com.touchtype.swiftkey",                // SwiftKey
            "com.oppo.input",                        // OPPO Keyboard
            "com.coloros.safecenter",                // ColorOS Security
        )
    }

    /** Apps that are always blocked in child mode during demo. */
    val restrictedApps: List<AppTile> = listOf(
        AppTile("youtube",    "YouTube",      "▶",  "Video",  isRestricted = true),
        AppTile("instagram",  "Instagram",    "📷", "Social", isRestricted = true),
        AppTile("tiktok",     "TikTok",       "🎵", "Video",  isRestricted = true),
        AppTile("twitter",    "X",            "🐦", "Social", isRestricted = true),
    )

    /** Apps always allowed in child mode. */
    val allowedApps: List<AppTile> = listOf(
        AppTile("calculator", "Calculator",   "🔢", "Education",   isRestricted = false),
        AppTile("maps",       "Maps",         "🗺", "Navigation",  isRestricted = false),
        AppTile("camera_app", "Camera",       "📸", "Utility",     isRestricted = false),
        AppTile("calendar",   "Calendar",     "📅", "Utility",     isRestricted = false),
        AppTile("clock",      "Clock",        "⏰", "Utility",     isRestricted = false),
        AppTile("notes",      "Notes",        "📝", "Education",   isRestricted = false),
    )

    /** All tiles for a given profile (restricted + allowed). */
    fun allTilesForProfile(profile: Profile): List<AppTile> {
        val liveRole = ageCalculator.computeRole(profile.dateOfBirth)
        return if (liveRole == Role.ADULT) {
            restrictedApps.map { it.copy(isRestricted = false) } + allowedApps
        } else {
            restrictedApps.map { tile ->
                val pkg = DEMO_PACKAGE_MAPPING[tile.id] ?: ""
                tile.copy(isRestricted = pkg in profile.restrictedPackages)
            } + allowedApps.map { tile ->
                val pkg = DEMO_PACKAGE_MAPPING[tile.id] ?: ""
                tile.copy(isRestricted = pkg in profile.restrictedPackages)
            }
        }
    }

    /**
     * Returns true if the given app is currently blocked for this profile.
     */
    fun isAppBlocked(profile: Profile, appId: String): Boolean {
        val liveRole = ageCalculator.computeRole(profile.dateOfBirth)
        if (liveRole == Role.ADULT) return false
        val pkg = DEMO_PACKAGE_MAPPING[appId] ?: return false
        return pkg in profile.restrictedPackages
    }

    /**
     * Human-readable reason for the block, shown in [BlockedOverlay].
     */
    fun blockReason(profile: Profile): String {
        return "This app is not allowed for ${profile.name}'s profile"
    }

    /**
     * Evaluate whether [packageName] is allowed for the current active session.
     *
     * @return [PolicyResult.Allow] if the package should run normally,
     *         [PolicyResult.Block] if the AccessibilityService should dismiss it,
     *         [PolicyResult.TimeUp] if the child's screen time is exhausted.
     */
    fun evaluate(packageName: String): PolicyResult {
        // Never block SmartGuard itself or system launchers/keyboards
        val ownPackage = context.packageName
        if (packageName == ownPackage || packageName in SYSTEM_PACKAGES) {
            return PolicyResult.Allow
        }

        val session = sessionManager.activeSession.value
        val appMode = session.appMode
        
        // 1. If Locked, block everything non-critical
        if (appMode == AppMode.LOCKED) {
            return PolicyResult.Block(packageName, "Device is locked", "Identification required")
        }

        val profile = session.activeProfile

        // 2. Safe Mode (Unknown User): Highly restrictive fallback
        if (appMode == AppMode.SAFE || profile == null) {
            // Check if it's one of the explicitly allowed demo apps (as a base)
            val isDemoAllowed = allowedApps.any { tile ->
                val pkg = DEMO_PACKAGE_MAPPING[tile.id] ?: tile.id
                pkg == packageName
            }
            if (isDemoAllowed) return PolicyResult.Allow
            
            Log.w(TAG, "BLOCK [$packageName] — Unknown user in Safe Mode")
            return PolicyResult.Block(packageName, packageName, "Unknown user. System access restricted.")
        }

        // 3. Identified User: Apply per-role policy
        val liveRole = ageCalculator.computeRole(profile.dateOfBirth)

        if (liveRole == Role.ADULT) {
            return PolicyResult.Allow
        }

        // Child/Teen: check screen time first
        if (session.remainingScreenTimeMinutes <= 0) {
            Log.w(TAG, "BLOCK [$packageName] — screen time exhausted for ${profile.name}")
            return PolicyResult.TimeUp(profile.name)
        }

        // Check against the profile's restricted list
        if (packageName in profile.restrictedPackages) {
            val reason = "This app is not allowed for ${profile.name}'s profile"
            Log.w(TAG, "BLOCK [$packageName] — in restrictedPackages for ${profile.name}")
            return PolicyResult.Block(packageName, packageName, reason)
        }

        return PolicyResult.Allow
    }

    /** Convenience: returns true if [packageName] is currently blocked for the active session. */
    fun isBlocked(packageName: String): Boolean = evaluate(packageName) != PolicyResult.Allow
}
