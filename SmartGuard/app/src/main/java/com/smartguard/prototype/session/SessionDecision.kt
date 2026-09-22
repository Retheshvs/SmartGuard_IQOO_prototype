package com.smartguard.prototype.session

import com.smartguard.prototype.identity.IdentityResult

/** Decision produced by [SessionController] after processing one detection result. */
sealed class SessionDecision {

    /**
     * Still accumulating consecutive detections — UI should show the "Confirming…" state.
     *
     * @param result       The identity result that was just processed
     * @param currentCount How many consecutive matching detections have been recorded so far
     * @param requiredCount How many are needed to commit (always [SessionController.REQUIRED_CONSECUTIVE])
     */
    data class Confirming(
        val result: IdentityResult,
        val currentCount: Int,
        val requiredCount: Int
    ) : SessionDecision()

    /**
     * Two (or more) consecutive matching detections received — mode switch should fire now.
     *
     * @param result The confirmed identity result
     */
    data class Confirmed(val result: IdentityResult) : SessionDecision()
}
