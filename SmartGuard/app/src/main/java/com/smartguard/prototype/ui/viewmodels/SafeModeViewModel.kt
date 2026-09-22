package com.smartguard.prototype.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartguard.prototype.identity.IdentityResult
import com.smartguard.prototype.security.AdminAuth
import com.smartguard.prototype.session.SessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SafeModeNavEvent {
    object NavigateToEnrollment : SafeModeNavEvent()
    object NavigateToUnlock : SafeModeNavEvent()
}

data class SafeModeUiState(
    val title: String = "Safe Mode Active",
    val description: String = "System features and settings are restricted.",
    val isUnknownFace: Boolean = false
)

@HiltViewModel
class SafeModeViewModel @Inject constructor(
    private val sessionController: SessionController,
    val adminAuth: AdminAuth
) : ViewModel() {

    private val _uiState = MutableStateFlow(SafeModeUiState())
    val uiState: StateFlow<SafeModeUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<SafeModeNavEvent>()
    val navigationEvent: SharedFlow<SafeModeNavEvent> = _navigationEvent.asSharedFlow()

    init {
        viewModelScope.launch {
            sessionController.sessionState.collect { state ->
                val result = state.lastIdentityResult
                if (result is IdentityResult.Unknown) {
                    _uiState.value = SafeModeUiState(
                        title = "Unknown Face Detected",
                        description = "SmartGuard doesn't recognize this person. An Admin must enroll this profile to grant access.",
                        isUnknownFace = true
                    )
                } else if (result is IdentityResult.NoFaceDetected || result is IdentityResult.Timeout) {
                    _uiState.value = SafeModeUiState(
                        title = "Verification Timed Out",
                        description = "No face was recognized within the time limit. Entering restricted Safe Mode.",
                        isUnknownFace = false
                    )
                }
            }
        }
    }

    fun onAdminAuthSuccess() {
        _navigationEvent.tryEmit(SafeModeNavEvent.NavigateToEnrollment)
    }

    fun onTryUnlockAgain() {
        _navigationEvent.tryEmit(SafeModeNavEvent.NavigateToUnlock)
    }
}
