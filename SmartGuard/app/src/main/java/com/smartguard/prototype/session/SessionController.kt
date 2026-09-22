package com.smartguard.prototype.session

import com.smartguard.prototype.identity.FaceDetector
import com.smartguard.prototype.identity.IdentityResult
import com.smartguard.prototype.profile.Profile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Buffers consecutive identity detection results and applies the
 * **2-consecutive-detections** rule before committing to a mode switch.
 *
 * ## Rule
 * - A mode switch only fires after [REQUIRED_CONSECUTIVE] consecutive detections
 *   that all resolve to the **same** identity key.
 * - If two consecutive results conflict (e.g. Alex → Jamie), the buffer resets
 *   and starts accumulating from the newer result.
 * - A single uncertain or low-confidence detection does NOT switch modes.
 * - [IdentityResult.MultipleFaces] is never confirmed — it always resets.
 */
@Singleton
class SessionController @Inject constructor() {

    companion object {
        const val REQUIRED_CONSECUTIVE = FaceDetector.REQUIRED_CONSECUTIVE_DETECTIONS
    }

    private val recentResults = ArrayDeque<IdentityResult>(REQUIRED_CONSECUTIVE + 1)

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /**
     * Process one detection result. Returns the decision the caller should act on.
     * This function is thread-safe — called from ViewModel coroutine context.
     */
    @Synchronized
    fun processResult(
        result: IdentityResult,
        trigger: IdentityTrigger = IdentityTrigger.UNLOCK
    ): SessionDecision {
        val lastConfidence = (result as? IdentityResult.Matched)?.confidence ?: 0f

        // MultipleFaces always resets — never confirm when multiple people visible
        if (result is IdentityResult.MultipleFaces) {
            recentResults.clear()
            _sessionState.value = _sessionState.value.copy(
                isConfirming = false,
                confirmationCount = 0,
                lastTrigger = trigger,
                lastConfidence = 0f,
                lastIdentityResult = result
            )
            return SessionDecision.Confirming(result, 0, REQUIRED_CONSECUTIVE)
        }

        // NoFaceDetected: don't advance confirmation, just update state
        if (result is IdentityResult.NoFaceDetected) {
            _sessionState.value = _sessionState.value.copy(
                lastIdentityResult = result
            )
            return SessionDecision.Confirming(result, recentResults.size, REQUIRED_CONSECUTIVE)
        }

        // Add result to buffer (trim to required size)
        recentResults.addLast(result)
        while (recentResults.size > REQUIRED_CONSECUTIVE) recentResults.removeFirst()

        // Not enough results yet
        if (recentResults.size < REQUIRED_CONSECUTIVE) {
            _sessionState.value = _sessionState.value.copy(
                isConfirming = true,
                confirmationCount = recentResults.size,
                lastTrigger = trigger,
                lastConfidence = lastConfidence,
                lastIdentityResult = result
            )
            return SessionDecision.Confirming(result, recentResults.size, REQUIRED_CONSECUTIVE)
        }

        // Check all buffered results have the same identity key
        val firstKey = recentResults.first().identityKey()
        val allMatch = recentResults.all { it.identityKey() == firstKey }

        return if (allMatch) {
            // Confirmed! Clear buffer and signal switch
            recentResults.clear()
            _sessionState.value = _sessionState.value.copy(
                isConfirming = false,
                confirmationCount = 0,
                lastTrigger = trigger,
                lastConfidence = lastConfidence,
                lastIdentityResult = result
            )
            SessionDecision.Confirmed(result)
        } else {
            // Conflict — reset and start over with the latest result
            recentResults.clear()
            recentResults.addLast(result)
            _sessionState.value = _sessionState.value.copy(
                isConfirming = true,
                confirmationCount = 1,
                lastTrigger = trigger,
                lastConfidence = lastConfidence,
                lastIdentityResult = result
            )
            SessionDecision.Confirming(result, 1, REQUIRED_CONSECUTIVE)
        }
    }

    /** Called after a mode switch is committed to update the persisted session state. */
    fun updateCurrentProfile(profile: Profile?, mode: AppMode) {
        recentResults.clear()
        _sessionState.value = _sessionState.value.copy(
            currentProfile = profile,
            appMode = mode,
            isConfirming = false,
            confirmationCount = 0
        )
    }

    /** Full reset — clears buffer and resets to locked state. */
    fun reset() {
        recentResults.clear()
        _sessionState.value = SessionState()
    }
}
