package com.smartguard.prototype.security

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.smartguard.prototype.R

/**
 * Device Administrator implementation for SmartGuard.
 *
 * ## Purpose
 * - Prevents users (children) from easily uninstalling the app.
 * - When active, Android requires the user to first deactivate "Device Admin"
 *   in system settings before the "Uninstall" button becomes available.
 * - Deactivating this receiver should be protected by [AdminAuthGate] in the app UI.
 */
class SmartGuardDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "SmartGuardAdmin"
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled")
        Toast.makeText(context, R.string.admin_receiver_enabled, Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device Admin disabled")
        Toast.makeText(context, R.string.admin_receiver_disabled, Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        // This message is shown to the user when they try to deactivate admin rights
        return context.getString(R.string.admin_receiver_disable_warning)
    }
}
