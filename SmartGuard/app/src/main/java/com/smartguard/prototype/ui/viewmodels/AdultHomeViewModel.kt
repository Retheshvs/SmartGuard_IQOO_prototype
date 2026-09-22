package com.smartguard.prototype.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartguard.prototype.mode.ModeManager
import com.smartguard.prototype.profile.Profile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AdultHomeViewModel @Inject constructor(
    private val modeManager: ModeManager
) : ViewModel() {

    val currentProfile: StateFlow<Profile?> = modeManager.currentProfile
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), modeManager.currentProfile.value)

    fun lockAndSwitchUser() {
        modeManager.lock()
    }
}
