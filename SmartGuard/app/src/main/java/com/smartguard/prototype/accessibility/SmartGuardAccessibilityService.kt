package com.smartguard.prototype.accessibility

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import com.smartguard.prototype.MainActivity
import com.smartguard.prototype.R
import com.smartguard.prototype.policy.PolicyEngine
import com.smartguard.prototype.policy.PolicyResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * System-wide foreground-app monitor for SmartGuard access control.
 *
 * ## What this service does
 * - Listens for [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] — fires whenever a
 *   new Activity/window comes to the foreground.
 * - Extracts the foreground app's package name from the event.
 * - Asks [PolicyEngine] whether the package is allowed for the current active profile.
 * - If blocked: performs [GLOBAL_ACTION_HOME] to dismiss the app, then launches
 *   [MainActivity] to show [BlockedOverlay].
 * - If screen-time is exhausted: same dismiss + launches BlockedOverlay with TIME_UP reason.
 *
 * ## What this service does NOT do
 * - Does NOT read screen content, text, passwords, or any view hierarchy.
 * - Does NOT operate when SmartGuard is not installed or when no profile is active.
 * - Does NOT replace Android's actual lock screen — verification happens after unlock.
 */
@AndroidEntryPoint
class SmartGuardAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SmartGuardA11y"

        /** Broadcast action emitted when an app is blocked (consumed by MainActivity). */
        const val ACTION_APP_BLOCKED = "com.smartguard.prototype.ACTION_APP_BLOCKED"
        const val EXTRA_BLOCKED_PACKAGE = "extra_blocked_package"
        const val EXTRA_BLOCK_REASON = "extra_block_reason"

        /** Broadcast action emitted when screen time is exhausted. */
        const val ACTION_TIME_UP = "com.smartguard.prototype.ACTION_TIME_UP"
        const val EXTRA_PROFILE_NAME = "extra_profile_name"

        private const val ALERT_CHANNEL_ID = "smartguard_alerts"
        private const val ALERT_NOTIF_ID = 2002
    }

    @Inject lateinit var policyEngine: PolicyEngine
    @Inject lateinit var accessibilityStateMonitor: AccessibilityStateMonitor

    private var lastPackage: String? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockTimestamp: Long = 0L
    /** Debounce window: don't re-block the same package within 1.5 seconds */
    private val BLOCK_DEBOUNCE_MS = 1500L

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "SmartGuardAccessibilityService connected — monitoring active")
        accessibilityStateMonitor.refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return
        if (packageName.isBlank()) return
        
        Log.i(TAG, "SYSTEM-WIDE EVENT: Foreground app changed to -> $packageName")

        // Debounce rapid re-fires for the same blocked package
        val now = System.currentTimeMillis()
        if (packageName == lastBlockedPackage && (now - lastBlockTimestamp) < BLOCK_DEBOUNCE_MS) {
            return
        }

        lastPackage = packageName

        when (val result = policyEngine.evaluate(packageName)) {
            is PolicyResult.Allow -> {
                lastBlockedPackage = null
            }

            is PolicyResult.Block -> {
                Log.i(TAG, "ACTION: BLOCKING restricted app: $packageName (${result.appName}) — ${result.reason}")
                lastBlockedPackage = packageName
                lastBlockTimestamp = now

                // Dismiss the blocked app to the home screen
                performGlobalAction(GLOBAL_ACTION_HOME)

                // Launch MainActivity to show BlockedOverlay immediately
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = ACTION_APP_BLOCKED
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_BLOCKED_PACKAGE, result.appName)
                    putExtra(EXTRA_BLOCK_REASON, result.reason)
                }
                startActivity(intent)
            }

            is PolicyResult.TimeUp -> {
                Log.w(TAG, "ACTION: TIME UP for ${result.profileName} — blocking $packageName")
                lastBlockedPackage = packageName
                lastBlockTimestamp = now

                performGlobalAction(GLOBAL_ACTION_HOME)

                val intent = Intent(this, MainActivity::class.java).apply {
                    action = ACTION_TIME_UP
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_PROFILE_NAME, result.profileName)
                }
                startActivity(intent)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SmartGuardAccessibilityService interrupted")
        // Post a persistent notification alerting the parent that protection may be impaired
        postProtectionDisabledNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "SmartGuardAccessibilityService destroyed")
        accessibilityStateMonitor.refresh()
    }

    /**
     * Posts a persistent notification when the Accessibility Service is interrupted
     * or disabled, so the parent is immediately aware that child protection is degraded.
     */
    private fun postProtectionDisabledNotification() {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALERT_CHANNEL_ID,
                getString(R.string.notification_channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_alerts_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_protection_disabled_title))
            .setContentText(getString(R.string.notification_protection_disabled_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(ALERT_NOTIF_ID, notification)
    }
}
