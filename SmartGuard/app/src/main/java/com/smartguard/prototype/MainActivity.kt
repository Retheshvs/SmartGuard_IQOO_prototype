package com.smartguard.prototype

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.smartguard.prototype.accessibility.AccessibilityStateMonitor
import com.smartguard.prototype.accessibility.SmartGuardAccessibilityService
import com.smartguard.prototype.mode.ModeManager
import com.smartguard.prototype.session.AppMode
import com.smartguard.prototype.ui.components.BlockedOverlay
import com.smartguard.prototype.ui.navigation.Screen
import com.smartguard.prototype.ui.navigation.SmartGuardNavGraph
import com.smartguard.prototype.ui.theme.SmartGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    @Inject lateinit var modeManager: ModeManager
    @Inject lateinit var accessibilityStateMonitor: AccessibilityStateMonitor

    private var blockedPackage by mutableStateOf<String?>(null)
    private var blockReason by mutableStateOf<String?>(null)
    private var forceLockNavigation by mutableStateOf(false)

    private val blockingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                SmartGuardAccessibilityService.ACTION_APP_BLOCKED -> {
                    blockedPackage = intent.getStringExtra(SmartGuardAccessibilityService.EXTRA_BLOCKED_PACKAGE)
                    blockReason = intent.getStringExtra(SmartGuardAccessibilityService.EXTRA_BLOCK_REASON)
                    Log.d(TAG, "Broadcast received: APP BLOCKED -> $blockedPackage")
                }
                SmartGuardAccessibilityService.ACTION_TIME_UP -> {
                    blockedPackage = "Screen Time"
                    blockReason = "Time's up for ${intent.getStringExtra(SmartGuardAccessibilityService.EXTRA_PROFILE_NAME)}"
                    Log.d(TAG, "Broadcast received: TIME UP")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setupLockScreenFlags()
        enableEdgeToEdge()
        
        // Register receiver for background app blocking events
        val filter = IntentFilter().apply {
            addAction(SmartGuardAccessibilityService.ACTION_APP_BLOCKED)
            addAction(SmartGuardAccessibilityService.ACTION_TIME_UP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(blockingReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(blockingReceiver, filter)
        }

        setContent {
            val currentMode by modeManager.currentMode.collectAsStateWithLifecycle()
            
            // Auto-dismiss keyguard when transitioning out of LOCKED state
            LaunchedEffect(currentMode) {
                if (currentMode != AppMode.LOCKED) {
                    dismissKeyguard()
                }
            }

            // SmartGuardTheme re-composes with a new color scheme on every mode transition.
            SmartGuardTheme(mode = currentMode) {
                val navController = rememberNavController()

                LaunchedEffect(forceLockNavigation) {
                    if (forceLockNavigation) {
                        Log.d(TAG, "Navigating to UnlockScreen due to forceLockNavigation")
                        navController.navigate(Screen.Unlock.route) {
                            popUpTo(0) { inclusive = true }
                        }
                        forceLockNavigation = false
                    }
                }
                
                SmartGuardNavGraph(navController = navController)

                // Global overlay for background blocking events
                BlockedOverlay(
                    visible = blockedPackage != null,
                    appName = blockedPackage ?: "",
                    reason = blockReason ?: "",
                    onDismiss = {
                        blockedPackage = null
                        blockReason = null
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accessibilityStateMonitor.refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(blockingReceiver)
        } catch (_: Exception) {}
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        
        // Ensure the window is shown over the lock screen even if the activity is already running
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        Log.d(TAG, "handleIntent: action=${intent?.action}")
        
        when (intent?.action) {
            SmartGuardAccessibilityService.ACTION_APP_BLOCKED -> {
                blockedPackage = intent.getStringExtra(SmartGuardAccessibilityService.EXTRA_BLOCKED_PACKAGE)
                blockReason = intent.getStringExtra(SmartGuardAccessibilityService.EXTRA_BLOCK_REASON)
                Log.i(TAG, "Blocking overlay triggered via Intent: $blockedPackage")
            }
            SmartGuardAccessibilityService.ACTION_TIME_UP -> {
                blockedPackage = "Screen Time"
                blockReason = "Time's up for ${intent.getStringExtra(SmartGuardAccessibilityService.EXTRA_PROFILE_NAME)}"
                Log.i(TAG, "Time-up overlay triggered via Intent")
            }
            else -> {
                val isScreenWake = intent?.getBooleanExtra("EXTRA_SCREEN_WAKE_UNLOCK", false) == true
                if (isScreenWake) {
                    Log.i(TAG, "FORCING UNLOCK OVERLAY: System trigger detected")
                    modeManager.lock()
                    forceLockNavigation = true
                }
            }
        }
    }

    /**
     * Requests dismissal of the Android system keyguard after successful SmartGuard identity verification.
     * Note: This does not replace or bypass the phone's actual PIN/pattern/fingerprint security;
     * it simply unlocks the keyguard once SmartGuard has verified identity.
     */
    fun dismissKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    Log.d(TAG, "System keyguard dismiss succeeded after SmartGuard verification")
                }

                override fun onDismissError() {
                    Log.w(TAG, "System keyguard dismiss error")
                }

                override fun onDismissCancelled() {
                    Log.d(TAG, "System keyguard dismiss cancelled by user")
                }
            })
        }
    }
}
