package com.smartguard.prototype.session

import com.smartguard.prototype.profile.Profile

/**
 * Immutable snapshot of the current active session state.
 * Emitted by [SessionManager.activeSession].
 */
data class ActiveSession(
    /** The currently identified and verified profile. Null in Safe Mode. */
    val activeProfile: Profile? = null,
    /** Current operating mode derived from the active profile's role. */
    val appMode: AppMode = AppMode.LOCKED,
    /** What triggered the most recent identity verification or mode switch. */
    val trigger: IdentityTrigger = IdentityTrigger.NONE,
    /**
     * Remaining screen-time minutes for the active Child/Teen profile.
     * Updated by ScreenTimeManager's countdown tick. Zero for ADULT mode.
     */
    val remainingScreenTimeMinutes: Int = 0
)
