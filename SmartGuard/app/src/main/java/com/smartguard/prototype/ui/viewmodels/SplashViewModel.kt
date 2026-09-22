package com.smartguard.prototype.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartguard.prototype.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SplashUiState {
    object Loading : SplashUiState()
    object NavigateToFirstRun : SplashUiState()
    object NavigateToUnlock : SplashUiState()
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkProfiles()
    }

    fun checkProfiles() {
        viewModelScope.launch {
            _uiState.value = SplashUiState.Loading
            val adminProfile = profileRepository.getAdminProfile()
            if (adminProfile == null) {
                _uiState.value = SplashUiState.NavigateToFirstRun
            } else {
                _uiState.value = SplashUiState.NavigateToUnlock
            }
        }
    }
}
