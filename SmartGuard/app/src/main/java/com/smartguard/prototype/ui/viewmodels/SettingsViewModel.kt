package com.smartguard.prototype.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartguard.prototype.policy.PolicyEngine
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.ProfileRepository
import com.smartguard.prototype.screentime.ScreenTimeManager
import com.smartguard.prototype.security.ScreenUnlockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val screenTimeManager: ScreenTimeManager,
    private val screenUnlockManager: ScreenUnlockManager,
    val policyEngine: PolicyEngine
) : ViewModel() {

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

    val profiles: StateFlow<List<Profile>> = profileRepository.observeAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScreenWakeUnlockEnabled: StateFlow<Boolean> = screenUnlockManager.isScreenWakeUnlockEnabled

    fun setScreenWakeUnlockEnabled(enabled: Boolean) {
        screenUnlockManager.setScreenWakeUnlockEnabled(enabled)
    }

    fun clearError() {
        _errorEvent.value = null
    }

    fun updateBudget(profile: Profile, newBudgetMinutes: Int) {
        viewModelScope.launch {
            val updated = profile.copy(
                screenTimeBudgetMinutes = newBudgetMinutes,
                remainingScreenTimeMinutes = newBudgetMinutes
            )
            profileRepository.updateProfile(updated)
            screenTimeManager.setRemainingForProfile(profile.id, newBudgetMinutes)
        }
    }

    fun resetRemainingTime(profile: Profile) {
        viewModelScope.launch {
            val updated = profile.copy(remainingScreenTimeMinutes = profile.screenTimeBudgetMinutes)
            profileRepository.updateProfile(updated)
            screenTimeManager.setRemainingForProfile(profile.id, profile.screenTimeBudgetMinutes)
        }
    }
}
