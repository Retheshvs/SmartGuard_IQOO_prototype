package com.smartguard.prototype.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartguard.prototype.debug.DebugStateManager
import com.smartguard.prototype.debug.SimulatedPersona
import com.smartguard.prototype.identity.FaceRecognitionPipeline
import com.smartguard.prototype.identity.ProfileMatcher
import com.smartguard.prototype.mode.ModeManager
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.ProfileRepository
import com.smartguard.prototype.screentime.ScreenTimeManager
import com.smartguard.prototype.session.AppMode
import com.smartguard.prototype.session.SessionController
import com.smartguard.prototype.session.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugPanelViewModel @Inject constructor(
    private val debugStateManager: DebugStateManager,
    private val modeManager: ModeManager,
    private val sessionController: SessionController,
    private val screenTimeManager: ScreenTimeManager,
    private val profileRepository: ProfileRepository,
    private val recognitionPipeline: FaceRecognitionPipeline
) : ViewModel() {

    val currentMode: StateFlow<AppMode> = modeManager.currentMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), modeManager.currentMode.value)

    val currentProfile: StateFlow<Profile?> = modeManager.currentProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), modeManager.currentProfile.value)

    val sessionState: StateFlow<SessionState> = sessionController.sessionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), sessionController.sessionState.value)

    val remainingMinutes: StateFlow<Int> = screenTimeManager.currentRemainingMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), screenTimeManager.currentRemainingMinutes.value)

    val selectedPersona: StateFlow<SimulatedPersona> = debugStateManager.selectedPersona
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), debugStateManager.selectedPersona.value)

    val isDemoModeActive: StateFlow<Boolean> = debugStateManager.isDemoModeActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), debugStateManager.isDemoModeActive.value)

    val isTFLiteAvailable: Boolean
        get() = recognitionPipeline.isTFLiteAvailable

    val matchThreshold: Float = ProfileMatcher.MATCH_THRESHOLD

    fun setDemoModeActive(active: Boolean) {
        debugStateManager.setDemoModeActive(active)
    }

    fun forceSimulatePersona(persona: SimulatedPersona) {
        debugStateManager.setPersona(persona)
        viewModelScope.launch {
            when (persona) {
                SimulatedPersona.PARENT_ALEX -> {
                    val admin = profileRepository.getAdminProfile()
                    modeManager.forceMode(AppMode.ADULT, admin)
                }
                SimulatedPersona.CHILD_JAMIE -> {
                    val child = profileRepository.getChildProfile()
                    modeManager.forceMode(AppMode.CHILD, child)
                }
                SimulatedPersona.UNKNOWN -> {
                    modeManager.forceMode(AppMode.SAFE, null)
                }
            }
        }
    }

    fun reseedDatabase() {
        viewModelScope.launch {
            profileRepository.seedInitialProfilesIfNeeded()
        }
    }
}

