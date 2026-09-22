package com.smartguard.prototype.ui.viewmodels

import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartguard.prototype.identity.FaceDetectionEvent
import com.smartguard.prototype.identity.FaceDetector
import com.smartguard.prototype.identity.FaceRecognitionPipeline
import com.smartguard.prototype.identity.IdentityResult
import com.smartguard.prototype.identity.ProfileMatcher
import com.smartguard.prototype.mode.ModeManager
import com.smartguard.prototype.profile.AgeCalculator
import com.smartguard.prototype.profile.Role
import com.smartguard.prototype.session.AppMode
import com.smartguard.prototype.session.IdentityTrigger
import com.smartguard.prototype.session.SessionController
import com.smartguard.prototype.session.SessionDecision
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class UnlockNavEvent {
    object NavigateToAdultHome : UnlockNavEvent()
    object NavigateToChildHome : UnlockNavEvent()
    object NavigateToSafeMode : UnlockNavEvent()
}

data class UnlockUiState(
    val faces: List<Rect> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val statusText: String = "Position face in camera view",
    val confirmationCount: Int = 0,
    val isConfirming: Boolean = false,
    val isTimedOut: Boolean = false,
    val recognizedName: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class UnlockViewModel @Inject constructor(
    private val profileMatcher: ProfileMatcher,
    private val recognitionPipeline: FaceRecognitionPipeline,
    private val sessionController: SessionController,
    private val modeManager: ModeManager,
    private val ageCalculator: AgeCalculator
) : ViewModel() {

    companion object {
        private const val TAG = "UnlockViewModel"
    }

    private val _uiState = MutableStateFlow(UnlockUiState())
    val uiState: StateFlow<UnlockUiState> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<UnlockNavEvent>()
    val navigationEvent: SharedFlow<UnlockNavEvent> = _navigationEvent.asSharedFlow()

    private var timeoutJob: Job? = null

    init {
        startTimeoutTimer()
    }

    private fun startTimeoutTimer() {
        timeoutJob?.cancel()
        timeoutJob = viewModelScope.launch {
            delay(FaceDetector.VERIFICATION_TIMEOUT_MS)
            if (!_uiState.value.isConfirming) {
                Log.w(TAG, "Face verification timed out after ${FaceDetector.VERIFICATION_TIMEOUT_MS}ms — routing to Safe Mode")
                _uiState.value = _uiState.value.copy(
                    isTimedOut = true,
                    statusText = "Verification timed out. Entering Safe Mode…"
                )
                delay(1200L)
                modeManager.setMode(AppMode.SAFE, null, IdentityTrigger.UNLOCK)
                _navigationEvent.emit(UnlockNavEvent.NavigateToSafeMode)
            }
        }
    }

    fun onFaceDetectionEvent(event: FaceDetectionEvent) {
        if (_uiState.value.isTimedOut) return

        _uiState.value = _uiState.value.copy(
            faces = event.boundingBoxes,
            imageWidth = event.imageWidth,
            imageHeight = event.imageHeight
        )

        val faces = event.faces
        if (faces.isNotEmpty()) {
            val quality = recognitionPipeline.checkQuality(faces[0], event.imageWidth, event.imageHeight)
            if (quality !is FaceRecognitionPipeline.FaceQuality.Good) {
                val msg = when (quality) {
                    is FaceRecognitionPipeline.FaceQuality.TooFar -> quality.message
                    is FaceRecognitionPipeline.FaceQuality.PoorPose -> quality.message
                    is FaceRecognitionPipeline.FaceQuality.LowConfidence -> quality.message
                    else -> "Poor image quality"
                }
                _uiState.value = _uiState.value.copy(statusText = msg, isConfirming = false)
                return
            }
        }

        viewModelScope.launch {
            val result = profileMatcher.match(event)
            val decision = sessionController.processResult(result, IdentityTrigger.UNLOCK)

            when (decision) {
                is SessionDecision.Confirming -> {
                    val statusMsg = when (result) {
                        is IdentityResult.NoFaceDetected -> "Scanning for face…"
                        is IdentityResult.MultipleFaces -> "Multiple faces detected — ensure only 1 person"
                        is IdentityResult.Matched -> "Recognizing ${result.profile.name} (${decision.currentCount}/${decision.requiredCount})…"
                        is IdentityResult.Unknown -> "Unknown face detected"
                        is IdentityResult.Timeout -> "Face verification timed out"
                    }
                    _uiState.value = _uiState.value.copy(
                        isConfirming = decision.currentCount > 0,
                        confirmationCount = decision.currentCount,
                        statusText = statusMsg,
                        recognizedName = (result as? IdentityResult.Matched)?.profile?.name
                    )
                }
                is SessionDecision.Confirmed -> {
                    timeoutJob?.cancel()
                    handleConfirmedResult(decision.result)
                }
            }
        }
    }

    private suspend fun handleConfirmedResult(result: IdentityResult) {
        when (result) {
            is IdentityResult.Matched -> {
                val profile = result.profile
                // Derive role automatically using DOB logic
                val age = ageCalculator.calculateAge(profile.dateOfBirth)
                val derivedRole = ageCalculator.suggestedRole(profile.dateOfBirth)
                val effectiveRole = if (profile.role == Role.ADULT || derivedRole == Role.ADULT) {
                    Role.ADULT
                } else {
                    Role.CHILD
                }

                Log.i(
                    TAG,
                    "Confirmed identity: ${profile.name} (Stored Role: ${profile.role}, Calculated Age: $age, Derived Role: $derivedRole). Switching to $effectiveRole mode."
                )

                if (effectiveRole == Role.ADULT) {
                    modeManager.setMode(AppMode.ADULT, profile, IdentityTrigger.UNLOCK)
                    Log.i(TAG, "ACTION: Admin identified — allowing phone access.")
                    _navigationEvent.emit(UnlockNavEvent.NavigateToAdultHome)
                } else {
                    modeManager.setMode(AppMode.CHILD, profile, IdentityTrigger.UNLOCK)
                    _navigationEvent.emit(UnlockNavEvent.NavigateToChildHome)
                }
            }
            is IdentityResult.Unknown, is IdentityResult.MultipleFaces, is IdentityResult.NoFaceDetected, is IdentityResult.Timeout -> {
                Log.w(TAG, "Conclusive non-match ($result) — routing to Safe Mode")
                modeManager.setMode(AppMode.SAFE, null, IdentityTrigger.UNLOCK)
                _navigationEvent.emit(UnlockNavEvent.NavigateToSafeMode)
            }
        }
    }

    fun proceedToSafeMode() {
        viewModelScope.launch {
            timeoutJob?.cancel()
            modeManager.setMode(AppMode.SAFE, null, IdentityTrigger.UNLOCK)
            _navigationEvent.emit(UnlockNavEvent.NavigateToSafeMode)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timeoutJob?.cancel()
    }
}
