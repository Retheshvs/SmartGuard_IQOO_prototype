package com.smartguard.prototype.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.accessibility.AccessibilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors whether [SmartGuardAccessibilityService] is currently enabled in
 * Android's Accessibility Settings.
 *
 * This is checked by:
 *  - DebugPanelScreen (shows "Protection disabled" alert)
 *  - SmartGuardForegroundService (posts a persistent alert notification)
 *  - FirstRunSetupScreen (shows the Accessibility explainer step)
 *
 * ## Why this is necessary
 * Android does not fire any event to apps when the user manually disables an
 * accessibility service via Settings → Accessibility. The only reliable way
 * to detect this is to poll [AccessibilityManager.getEnabledAccessibilityServiceList]
 * before each policy evaluation, or whenever the app comes to foreground.
 *
 * Note: [SmartGuardAccessibilityService.onInterrupt] is called when the system
 * requests the service to stop, but the service's process may still be alive.
 * Checking AccessibilityManager is the ground truth.
 */
@Singleton
class AccessibilityStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val accessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager

    private val _isServiceEnabled = MutableStateFlow(checkEnabled())
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    /**
     * Re-check whether the service is enabled and update the StateFlow.
     * Call this from:
     *  - onResume in MainActivity
     *  - Periodically from SmartGuardForegroundService
     */
    fun refresh() {
        _isServiceEnabled.value = checkEnabled()
    }

    /**
     * Returns true if [SmartGuardAccessibilityService] appears in the list of
     * currently-enabled accessibility services.
     */
    fun checkEnabled(): Boolean {
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val ownPackage = context.packageName
        return enabledServices.any { serviceInfo ->
            serviceInfo.resolveInfo.serviceInfo.packageName == ownPackage
        }
    }
}
