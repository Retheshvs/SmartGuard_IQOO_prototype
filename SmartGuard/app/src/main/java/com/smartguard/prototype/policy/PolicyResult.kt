package com.smartguard.prototype.policy

/**
 * Result of a [PolicyEngine.evaluate] call.
 *
 * The AccessibilityService uses this to decide whether to perform GLOBAL_ACTION_HOME
 * and show BlockedOverlay.
 */
sealed class PolicyResult {
    /** App is allowed for the current profile. No action needed. */
    object Allow : PolicyResult()

    /**
     * App is in the profile's [restrictedPackages] list.
     *
     * @param blockedPackage The package name that triggered the block.
     * @param appName        User-friendly name of the blocked app.
     * @param reason         Human-readable explanation shown in BlockedOverlay.
     */
    data class Block(val blockedPackage: String, val appName: String, val reason: String) : PolicyResult()

    /**
     * Child's [remainingScreenTimeMinutes] has reached zero.
     *
     * @param profileName Used to personalize the "Time's up" message.
     */
    data class TimeUp(val profileName: String) : PolicyResult()
}
