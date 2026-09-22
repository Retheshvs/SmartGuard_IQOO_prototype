package com.smartguard.prototype.identity

import com.smartguard.prototype.profile.Profile

/**
 * Sealed hierarchy representing the outcome of an identity verification attempt.
 *
 * In this implementation, the [Matched] case is produced by [ProfileMatcher]
 * after comparing a live face embedding vector against enrolled profiles
 * in the Room database using Euclidean distance.
 */
sealed class IdentityResult {

    /** A single face was detected and matched to a known profile. */
    data class Matched(
        val profile: Profile,
        /** Similarity confidence score 0–1 (1.0 = perfect match) */
        val confidence: Float
    ) : IdentityResult()

    /** A single face was detected but could not be matched to any enrolled profile. */
    object Unknown : IdentityResult()

    /** More than one face was detected simultaneously — cannot safely identify. */
    object MultipleFaces : IdentityResult()

    /** No face was detected in this frame. */
    object NoFaceDetected : IdentityResult()

    /** Verification timed out without a conclusive result. */
    object Timeout : IdentityResult()

    /**
     * Returns a stable string key identifying the identity (not the result type).
     * Used by [com.smartguard.prototype.session.SessionController] to compare
     * consecutive detections.
     */
    fun identityKey(): String = when (this) {
        is Matched      -> profile.id
        is Unknown      -> "unknown"
        is MultipleFaces -> "multiple"
        is NoFaceDetected -> "none"
        is Timeout      -> "timeout"
    }
}
