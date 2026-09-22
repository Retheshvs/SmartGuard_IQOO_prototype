package com.smartguard.prototype.service

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smartguard.prototype.MainActivity
import com.smartguard.prototype.R
import com.smartguard.prototype.accessibility.AccessibilityStateMonitor
import com.smartguard.prototype.screentime.ScreenTimeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Persistent foreground service that keeps SmartGuard's monitoring alive even when
 * the app's Activity has been swiped from Recents or sent to background.
 *
 * ## Responsibilities
 * 1. Shows a low-priority persistent notification ("SmartGuard Active") so Android's
 *    system does not aggressively kill the process.
 * 2. Periodically refreshes [AccessibilityStateMonitor] to detect if the Accessibility
 *    Service was disabled externally, posting a high-priority alert if so.
 * 3. Persists [ScreenTimeManager]'s current countdown to Room on a regular interval
 *    so no more than ~1 minute of screen time is lost if the process is killed.
 * 4. Overrides [onTaskRemoved] to reschedule its own restart via [AlarmManager] if the
 *    user swipes SmartGuard from the Recents task list.
 *
 * ## Starting this service
 * Started in [SmartGuardApp.onCreate] and by [BootReceiver] on device boot.
 * Must be started as a foreground service (API 26+).
 *
 * ## Camera note
 * The foreground service type is declared as `camera` in the manifest to satisfy
 * Android 14+ requirements for camera access from background contexts. The camera
 * is only opened inside the Identity pipeline when verification is triggered.
 */
@AndroidEntryPoint
class SmartGuardForegroundService : Service() {

    companion object {
        private const val TAG = "SmartGuardSvc"
        const val CHANNEL_ID_PROTECTION = "smartguard_protection"
        const val NOTIF_ID_PROTECTION = 1001

        /** Check accessibility + persist screen time every 60 seconds. */
        private const val MONITOR_INTERVAL_MS = 60_000L

        const val ACTION_START = "com.smartguard.prototype.ACTION_START_SERVICE"
        const val ACTION_STOP  = "com.smartguard.prototype.ACTION_STOP_SERVICE"

        fun startIntent(context: Context): Intent =
            Intent(context, SmartGuardForegroundService::class.java).apply {
                action = ACTION_START
            }
    }

    @Inject lateinit var accessibilityStateMonitor: AccessibilityStateMonitor
    @Inject lateinit var screenTimeManager: ScreenTimeManager

    private val unlockTriggerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_USER_PRESENT) {
                Log.i(TAG, "ACTION_USER_PRESENT received (System Unlocked) — launching identity verification")
                val unlockIntent = Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra("EXTRA_SCREEN_WAKE_UNLOCK", true)
                }
                try {
                    context?.startActivity(unlockIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start MainActivity from unlock receiver: ${e.message}")
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID_PROTECTION, buildProtectionNotification())
        
        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
        registerReceiver(unlockTriggerReceiver, filter)
        
        Log.i(TAG, "SmartGuardForegroundService started — listening for ACTION_USER_PRESENT")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startMonitorLoop()
        }
        // START_STICKY: OS restarts the service after killing it (no Intent replayed)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * When the user swipes the app from the Recents task list, schedule a restart
     * via AlarmManager so monitoring is resumed within 2 seconds.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "Task removed from Recents — scheduling service restart")
        val restartIntent = startIntent(this)
        val pendingIntent = PendingIntent.getService(
            this, 1, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val restartAt = System.currentTimeMillis() + 2_000L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restartAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, restartAt, pendingIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(unlockTriggerReceiver)
        } catch (_: Exception) {}
        monitorJob?.cancel()
        serviceScope.cancel()
        Log.i(TAG, "SmartGuardForegroundService destroyed")
    }

    // ─────────────────────────────────────────────────────────────────────────

    private fun startMonitorLoop() {
        if (monitorJob?.isActive == true) return

        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(MONITOR_INTERVAL_MS)

                // Refresh accessibility service state
                accessibilityStateMonitor.refresh()

                // Persist screen time so we don't lose more than ~1 minute on kill
                try {
                    screenTimeManager.persistCurrentIfNeeded()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist screen time in monitor loop", e)
                }

                // Update notification if accessibility service was disabled
                if (!accessibilityStateMonitor.isServiceEnabled.value) {
                    updateNotificationProtectionDisabled()
                } else {
                    updateNotificationProtectionActive()
                }
            }
        }
    }

    private fun buildProtectionNotification(): android.app.Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID_PROTECTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_protection_active_title))
            .setContentText(getString(R.string.notification_protection_active_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
    }

    private fun updateNotificationProtectionActive() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_PROTECTION, buildProtectionNotification())
    }

    private fun updateNotificationProtectionDisabled() {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID_PROTECTION)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_protection_disabled_title))
            .setContentText(getString(R.string.notification_protection_disabled_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID_PROTECTION, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PROTECTION,
                getString(R.string.notification_channel_protection_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_protection_desc)
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
