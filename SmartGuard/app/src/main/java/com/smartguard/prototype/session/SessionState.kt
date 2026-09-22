package com.smartguard.prototype.session

import com.smartguard.prototype.identity.IdentityResult
import com.smartguard.prototype.profile.Profile

data class SessionState(
    val currentProfile: Profile? = null,
    val appMode: AppMode = AppMode.LOCKED,
    val isConfirming: Boolean = false,
    val confirmationCount: Int = 0,
    val lastTrigger: IdentityTrigger = IdentityTrigger.NONE,
    val lastConfidence: Float = 0f,
    val lastIdentityResult: IdentityResult? = null
)

/** Current operating mode of the app. */
enum class AppMode {
    /** Unlocked, adult content policy applied */
    ADULT,
    /** Unlocked, child content policy applied, screen-time active */
    CHILD,
    /** Unlocked, teen content policy applied, screen-time active */
    TEEN,
    /** Unrecognised face — restricted default view */
    SAFE,
    /** App is locked; camera inactive */
    LOCKED
}

/** What triggered the most recent identity verification. */
enum class IdentityTrigger {
    /** App opened from cold start or screen wake. */
    UNLOCK,
    /** App resumed from background. */
    RESUME,
    /** A protected action (settings, enrollment) was initiated. */
    PROTECTED_ACTION,
    /** Debug-panel forced simulation — never used in primary flow. */
    MANUAL,
    /** No trigger recorded yet. */
    NONE
}
