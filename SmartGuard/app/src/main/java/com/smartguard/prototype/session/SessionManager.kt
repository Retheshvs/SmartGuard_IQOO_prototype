package com.smartguard.prototype.session

import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.Role
import com.smartguard.prototype.screentime.ScreenTimeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central authority for the active user session.
 *
 * All mode transitions go through [activateProfile] or [activateSafeMode].
 * Observers include MainActivity (theme), ChildHomeScreen (screen time bar),
 * PolicyEngine (access decisions), and the AccessibilityService.
 *
 * Screen-time continuity rule:
 * When a Child's session ends (e.g., Parent picks up the phone), [ScreenTimeManager]
 * freezes the countdown. When the Child's session resumes, [ScreenTimeManager] resumes
 * from exactly where it stopped — never resets the remaining time.
 */
@Singleton
class SessionManager @Inject constructor(
    private val screenTimeManager: ScreenTimeManager
) {
    private val _activeSession = MutableStateFlow(ActiveSession())
    val activeSession: StateFlow<ActiveSession> = _activeSession.asStateFlow()

    /**
     * Switch the active session to [profile].
     *
     * - If [profile.role] is CHILD or TEEN, starts/resumes the screen-time countdown.
     * - If [profile.role] is ADULT, pauses the countdown (preserving the child's remaining time).
     * - Never resets any profile's screen-time unless explicitly called by the parent
     *   via SettingsScreen.
     */
    suspend fun activateProfile(profile: Profile, trigger: IdentityTrigger) {
        // Pause the previous session's countdown (if any child session was running)
        val previous = _activeSession.value
        if (previous.activeProfile?.role in listOf(Role.CHILD, Role.TEEN)) {
            screenTimeManager.pauseCountdown()
        }

        // Initialize and start the new profile's screen time if child/teen
        val appMode = when (profile.role) {
            Role.ADULT -> AppMode.ADULT
            Role.CHILD -> AppMode.CHILD
            Role.TEEN  -> AppMode.TEEN
        }

        val remaining = screenTimeManager.getRemainingForProfile(profile.id)
            .takeIf { it > 0 }
            ?: profile.remainingScreenTimeMinutes

        _activeSession.value = ActiveSession(
            activeProfile = profile,
            appMode = appMode,
            trigger = trigger,
            remainingScreenTimeMinutes = remaining
        )

        if (profile.role in listOf(Role.CHILD, Role.TEEN)) {
            screenTimeManager.initializeForProfile(profile)
            screenTimeManager.startCountdown(profile) { newRemaining ->
                _activeSession.value = _activeSession.value.copy(
                    remainingScreenTimeMinutes = newRemaining
                )
            }
        }
    }

    /**
     * Switch to Safe Mode — unknown/unenrolled/timeout result.
     * Pauses any running child countdown.
     */
    fun activateSafeMode(trigger: IdentityTrigger) {
        val previous = _activeSession.value
        if (previous.activeProfile?.role in listOf(Role.CHILD, Role.TEEN)) {
            screenTimeManager.pauseCountdown()
        }
        _activeSession.value = ActiveSession(
            activeProfile = null,
            appMode = AppMode.SAFE,
            trigger = trigger,
            remainingScreenTimeMinutes = 0
        )
    }

    /**
     * Switch to LOCKED state — no profile active, everything blocked.
     */
    fun lock() {
        val previous = _activeSession.value
        if (previous.activeProfile?.role in listOf(Role.CHILD, Role.TEEN)) {
            screenTimeManager.pauseCountdown()
        }
        _activeSession.value = ActiveSession(
            activeProfile = null,
            appMode = AppMode.LOCKED,
            trigger = IdentityTrigger.NONE,
            remainingScreenTimeMinutes = 0
        )
    }

    /**
     * Force mode for debug-only persona simulation.
     * Must never be called from primary flow.
     */
    suspend fun forceSimulate(profile: Profile?) {
        if (profile == null) {
            activateSafeMode(IdentityTrigger.MANUAL)
            return
        }
        activateProfile(profile, IdentityTrigger.MANUAL)
    }
}
