package com.smartguard.prototype.mode

import com.smartguard.prototype.di.ApplicationScope
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.session.AppMode
import com.smartguard.prototype.session.IdentityTrigger
import com.smartguard.prototype.session.SessionController
import com.smartguard.prototype.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative source for the current [AppMode] and active [Profile].
 *
 * All mode transitions (real or simulated) go through this class.
 * [MainActivity] observes [currentMode] to drive [SmartGuardTheme] color scheme switching.
 */
@Singleton
class ModeManager @Inject constructor(
    private val sessionController: SessionController,
    private val sessionManager: SessionManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    private val _currentMode = MutableStateFlow(AppMode.LOCKED)
    val currentMode: StateFlow<AppMode> = _currentMode.asStateFlow()

    private val _currentProfile = MutableStateFlow<Profile?>(null)
    val currentProfile: StateFlow<Profile?> = _currentProfile.asStateFlow()

    /**
     * Transition to a new mode after a real identity verification.
     *
     * @param mode    Resolved mode for the identified profile
     * @param profile Identified profile (null for Safe mode)
     * @param trigger What triggered the verification
     */
    fun setMode(mode: AppMode, profile: Profile? = null, trigger: IdentityTrigger = IdentityTrigger.UNLOCK) {
        _currentMode.value = mode
        _currentProfile.value = profile
        sessionController.updateCurrentProfile(profile, mode)
        
        // Sync with SessionManager to ensure PolicyEngine and ScreenTimeManager are active
        applicationScope.launch {
            if (mode == AppMode.SAFE || profile == null) {
                sessionManager.activateSafeMode(trigger)
            } else {
                sessionManager.activateProfile(profile, trigger)
            }
        }
    }

    /**
     * Force a mode switch for Demo Mode — bypasses the camera flow entirely.
     * Called by [DebugPanelViewModel.forceSimulatePersona].
     */
    fun forceMode(mode: AppMode, profile: Profile?) {
        setMode(mode, profile, IdentityTrigger.MANUAL)
    }

    /** Returns to LOCKED state; clears current profile. Used on handover triggers. */
    fun lock() {
        _currentMode.value = AppMode.LOCKED
        _currentProfile.value = null
        sessionController.reset()
        sessionManager.lock() // Lock the session too
    }
}
