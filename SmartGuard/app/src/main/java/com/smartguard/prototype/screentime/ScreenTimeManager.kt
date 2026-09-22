package com.smartguard.prototype.screentime

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.smartguard.prototype.di.ApplicationScope
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.ProfileRepository
import com.smartguard.prototype.profile.Role
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages per-profile screen-time countdowns.
 *
 * ## Screen-time continuity guarantee (Acceptance Criterion #11)
 * - Remaining time is held in-memory in [remainingTimeCache] and persisted to Room
 *   every [PERSIST_EVERY_N_TICKS] ticks.
 * - When a Child profile becomes active, the countdown **resumes** from the cached value —
 *   never resets to the budget.
 * - When an Adult profile takes over, [pauseCountdown] stops the tick without touching the cache.
 * - This preserves the correct remaining time across a Parent → Child → Parent → Child cycle.
 *
 * ## Screen-on Tracking
 * Uses [PowerManager.isInteractive] to ensure time is only decremented when the screen is physically on.
 */
@Singleton
class ScreenTimeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    companion object {
        private const val TAG = "ScreenTimeManager"
        /** How often the countdown decrements — 60 seconds per tick = 1 minute. */
        private const val TICK_INTERVAL_MS = 60_000L
        /** Persist to Room every N ticks to reduce write frequency. */
        private const val PERSIST_EVERY_N_TICKS = 5
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /** In-memory per-profile remaining minutes. Survives mode switches within a session. */
    private val remainingTimeCache = ConcurrentHashMap<String, Int>()

    /** Exposed for legacy observers (ChildHomeViewModel direct subscription). */
    private val _currentRemainingMinutes = MutableStateFlow(0)
    val currentRemainingMinutes: StateFlow<Int> = _currentRemainingMinutes.asStateFlow()

    private var activeProfileId: String? = null
    private var countdownJob: Job? = null
    private var tickCallback: ((Int) -> Unit)? = null

    /**
     * Load [profile]'s remaining time into the in-memory cache.
     * If already cached (from a previous session this run), use the cached value.
     * Otherwise, load from the persisted Room value.
     */
    suspend fun initializeForProfile(profile: Profile) {
        val cached = remainingTimeCache[profile.id]
        val remaining = cached ?: profile.remainingScreenTimeMinutes
        remainingTimeCache[profile.id] = remaining
        _currentRemainingMinutes.value = remaining
        Log.d(TAG, "Initialized ${profile.name}: ${remaining}m remaining")
    }

    /**
     * Start a 1-minute-tick countdown for [profile].
     *
     * @param onTick Called every minute with the new remaining-minutes value.
     *               SessionManager uses this to update [ActiveSession.remainingScreenTimeMinutes].
     *
     * Idempotent: if already running for the same profile, does nothing.
     * No-op for ADULT profiles.
     */
    fun startCountdown(profile: Profile, onTick: (Int) -> Unit) {
        if (profile.role == Role.ADULT) return

        val current = remainingTimeCache[profile.id] ?: profile.remainingScreenTimeMinutes
        _currentRemainingMinutes.value = current
        tickCallback = onTick

        // Avoid restarting if already running for same profile
        if (activeProfileId == profile.id && countdownJob?.isActive == true) return

        activeProfileId = profile.id
        countdownJob?.cancel()

        countdownJob = applicationScope.launch {
            var tickCount = 0
            while (isActive && (_currentRemainingMinutes.value) > 0) {
                delay(TICK_INTERVAL_MS)
                
                // Only decrement if the screen is physically ON
                if (!powerManager.isInteractive) {
                    Log.d(TAG, "Screen OFF — skipping tick for ${profile.name}")
                    continue
                }

                val newValue = (_currentRemainingMinutes.value - 1).coerceAtLeast(0)
                _currentRemainingMinutes.value = newValue
                remainingTimeCache[profile.id] = newValue
                tickCallback?.invoke(newValue)
                tickCount++

                // Persist every N ticks or when exhausted
                if (tickCount % PERSIST_EVERY_N_TICKS == 0 || newValue == 0) {
                    try {
                        profileRepository.updateRemainingTime(profile.id, newValue)
                        Log.d(TAG, "Persisted: ${profile.name} = ${newValue}m")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to persist screen time", e)
                    }
                }
            }
            Log.d(TAG, "Countdown ended for ${profile.id} — time exhausted")
        }
    }

    /**
     * Pause the countdown without resetting the cached value.
     * Called when the active profile switches to an ADULT (or Safe Mode).
     */
    fun pauseCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        tickCallback = null
        Log.d(TAG, "Countdown paused. Cache state: $remainingTimeCache")
    }

    /** Returns the cached remaining minutes for a profile (0 if not initialized). */
    fun getRemainingForProfile(profileId: String): Int =
        remainingTimeCache[profileId] ?: 0

    /**
     * Directly set the remaining time for a profile.
     * Called by SettingsScreen when the parent overrides or resets the budget.
     */
    fun setRemainingForProfile(profileId: String, minutes: Int) {
        remainingTimeCache[profileId] = minutes
        if (activeProfileId == profileId) {
            _currentRemainingMinutes.value = minutes
        }
    }

    /** Persist the current countdown value before app goes to background or process is killed. */
    suspend fun persistCurrentIfNeeded() {
        val profileId = activeProfileId ?: return
        val remaining = remainingTimeCache[profileId] ?: return
        try {
            profileRepository.updateRemainingTime(profileId, remaining)
            Log.d(TAG, "Persisted on pause: ${remaining}m for $profileId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist on pause", e)
        }
    }
}
