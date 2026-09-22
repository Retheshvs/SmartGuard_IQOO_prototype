package com.smartguard.prototype.debug

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The three possible simulated personas for demo/debug mode. */
enum class SimulatedPersona(val displayName: String) {
    PARENT_ALEX("Alex (Parent)"),
    CHILD_JAMIE("Jamie (Child)"),
    UNKNOWN("Unknown Person")
}

/**
 * Manages debug/demo state that persists in [SharedPreferences].
 *
 * - [selectedPersona] controls which identity [ProfileMatcher] resolves to
 *   when a face is detected by the camera pipeline.
 * - [isDemoModeActive] activates the visual Demo Mode banner and enables the
 *   "Simulate User" instant-switch buttons in [DebugPanelScreen].
 *
 * Both values survive app restarts so a demo presenter doesn't have to
 * re-configure after relaunching.
 */
@Singleton
class DebugStateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smartguard_debug_prefs", Context.MODE_PRIVATE)

    private val _selectedPersona = MutableStateFlow(
        SimulatedPersona.valueOf(
            prefs.getString("selected_persona", SimulatedPersona.UNKNOWN.name)
                ?: SimulatedPersona.UNKNOWN.name
        )
    )
    val selectedPersona: StateFlow<SimulatedPersona> = _selectedPersona.asStateFlow()

    private val _isDemoModeActive = MutableStateFlow(
        prefs.getBoolean("demo_mode_active", false)
    )
    val isDemoModeActive: StateFlow<Boolean> = _isDemoModeActive.asStateFlow()

    fun setPersona(persona: SimulatedPersona) {
        _selectedPersona.value = persona
        prefs.edit().putString("selected_persona", persona.name).apply()
    }

    fun setDemoModeActive(active: Boolean) {
        _isDemoModeActive.value = active
        prefs.edit().putBoolean("demo_mode_active", active).apply()
    }
}
