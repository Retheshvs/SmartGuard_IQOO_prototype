package com.smartguard.prototype.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileDetailUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ProfileDetailViewModel @Inject constructor(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileDetailUiState())
    val uiState: StateFlow<ProfileDetailUiState> = _uiState.asStateFlow()

    fun loadProfile(profileId: String) {
        viewModelScope.launch {
            _uiState.value = ProfileDetailUiState(isLoading = true)
            val profile = profileRepository.getProfileById(profileId)
            if (profile != null) {
                _uiState.value = ProfileDetailUiState(isLoading = false, profile = profile)
            } else {
                _uiState.value = ProfileDetailUiState(isLoading = false, errorMessage = "Profile not found")
            }
        }
    }
}
