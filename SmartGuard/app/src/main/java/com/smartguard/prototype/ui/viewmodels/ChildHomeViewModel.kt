package com.smartguard.prototype.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartguard.prototype.mode.ModeManager
import com.smartguard.prototype.policy.AppTile
import com.smartguard.prototype.policy.PolicyEngine
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.screentime.ScreenTimeManager
import com.smartguard.prototype.security.AdminAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChildHomeUiState(
    val currentProfile: Profile? = null,
    val remainingMinutes: Int = 0,
    val budgetMinutes: Int = 60,
    val appTiles: List<AppTile> = emptyList(),
    val blockedTile: AppTile? = null,
    val blockReason: String = ""
)

@HiltViewModel
class ChildHomeViewModel @Inject constructor(
    private val modeManager: ModeManager,
    private val screenTimeManager: ScreenTimeManager,
    private val policyEngine: PolicyEngine,
    val adminAuth: AdminAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChildHomeUiState())
    val uiState: StateFlow<ChildHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modeManager.currentProfile.collect { profile ->
                if (profile != null) {
                    screenTimeManager.initializeForProfile(profile)
                    screenTimeManager.startCountdown(profile) { /* handled by collector below */ }
                    val tiles = policyEngine.allTilesForProfile(profile)
                    _uiState.value = _uiState.value.copy(
                        currentProfile = profile,
                        budgetMinutes = profile.screenTimeBudgetMinutes,
                        appTiles = tiles
                    )
                }
            }
        }

        viewModelScope.launch {
            screenTimeManager.currentRemainingMinutes.collect { remaining ->
                _uiState.value = _uiState.value.copy(remainingMinutes = remaining)
            }
        }
    }

    fun onAppTileClicked(tile: AppTile) {
        val profile = _uiState.value.currentProfile ?: return
        if (policyEngine.isAppBlocked(profile, tile.id)) {
            val reason = policyEngine.blockReason(profile)
            _uiState.value = _uiState.value.copy(
                blockedTile = tile,
                blockReason = reason
            )
        } else {
            // Unrestricted tile clicked - no block
        }
    }

    fun dismissBlockedOverlay() {
        _uiState.value = _uiState.value.copy(blockedTile = null, blockReason = "")
    }

    fun lockAndSwitchUser() {
        screenTimeManager.pauseCountdown()
        modeManager.lock()
    }

    override fun onCleared() {
        super.onCleared()
        screenTimeManager.pauseCountdown()
    }
}
