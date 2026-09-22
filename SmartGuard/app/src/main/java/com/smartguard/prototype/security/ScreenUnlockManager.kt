package com.smartguard.prototype.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.util.Log
import com.smartguard.prototype.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages screen-wake lock screen verification behavior.
 *
 * Registers a [BroadcastReceiver] listening for [Intent.ACTION_SCREEN_ON] and [Intent.ACTION_USER_PRESENT].
 * When screen-wake verification is enabled and the display turns on:
 * Launches [MainActivity] over the system lock screen using window display flags.
 *
 * Disclaimer:
 * This does NOT replace or bypass the device's native PIN/pattern/biometric lock screen.
 * The system keyguard remains active underneath; SmartGuard provides an additional
 * face verification layer on screen wake and requests keyguard dismissal on successful match.
 */
@Singleton
class ScreenUnlockManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "ScreenUnlockManager"
        private const val PREFS_NAME = "smartguard_screen_unlock_prefs"
        private const val KEY_ENABLED = "screen_wake_unlock_enabled"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isScreenWakeUnlockEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_ENABLED, true)
    )
    val isScreenWakeUnlockEnabled: StateFlow<Boolean> = _isScreenWakeUnlockEnabled.asStateFlow()

    private var isReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action
            Log.d(TAG, "Screen broadcast received: $action")

            if ((action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_USER_PRESENT) && _isScreenWakeUnlockEnabled.value) {
                Log.d(TAG, "Screen wake detected — launching SmartGuard Unlock over lock screen")
                launchUnlockActivity()
            }
        }
    }

    fun setScreenWakeUnlockEnabled(enabled: Boolean) {
        _isScreenWakeUnlockEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.d(TAG, "Screen wake unlock enabled set to: $enabled")
    }

    fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        context.registerReceiver(screenReceiver, filter)
        isReceiverRegistered = true
        Log.d(TAG, "Screen wake BroadcastReceiver registered")
    }

    fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(screenReceiver)
            isReceiverRegistered = false
            Log.d(TAG, "Screen wake BroadcastReceiver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    private fun launchUnlockActivity() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("EXTRA_SCREEN_WAKE_UNLOCK", true)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity on screen wake", e)
        }
    }
}
