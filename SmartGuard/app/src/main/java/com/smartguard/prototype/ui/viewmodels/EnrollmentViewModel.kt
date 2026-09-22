package com.smartguard.prototype.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.face.Face
import com.smartguard.prototype.identity.FaceEmbeddingExtractor
import com.smartguard.prototype.identity.FaceRecognitionPipeline
import com.smartguard.prototype.profile.AgeCalculator
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.ProfileRepository
import com.smartguard.prototype.profile.Role
import com.smartguard.prototype.security.AdminAuth
import com.smartguard.prototype.security.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class EnrollmentUiState(
    val profileId: String = UUID.randomUUID().toString(),
    val isEditMode: Boolean = false,
    val name: String = "",
    val role: Role = Role.CHILD,
    val dateOfBirthMillis: Long = System.currentTimeMillis() - (10L * 365 * 24 * 3600 * 1000), // ~10 yrs default for child
    val childPin: String = "1234",
    val screenTimeBudgetMinutes: Int = 60,
    val faceImagePath: String? = null,
    val faceEmbedding: FloatArray? = null,
    val capturedSamples: List<FloatArray> = emptyList(),
    val requiredSamples: Int = 5,
    val fingerprintEnrolled: Boolean = false,
    val isAdmin: Boolean = false,
    val restrictedPackages: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isSaved: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EnrollmentUiState
        if (profileId != other.profileId) return false
        if (isEditMode != other.isEditMode) return false
        if (name != other.name) return false
        if (role != other.role) return false
        if (dateOfBirthMillis != other.dateOfBirthMillis) return false
        if (childPin != other.childPin) return false
        if (screenTimeBudgetMinutes != other.screenTimeBudgetMinutes) return false
        if (faceImagePath != other.faceImagePath) return false
        if (faceEmbedding != null) {
            if (other.faceEmbedding == null) return false
            if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false
        } else if (other.faceEmbedding != null) return false
        if (fingerprintEnrolled != other.fingerprintEnrolled) return false
        if (isAdmin != other.isAdmin) return false
        if (isSaving != other.isSaving) return false
        if (errorMessage != other.errorMessage) return false
        if (isSaved != other.isSaved) return false
        if (restrictedPackages != other.restrictedPackages) return false
        return true
    }

    override fun hashCode(): Int {
        var result = profileId.hashCode()
        result = 31 * result + isEditMode.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + dateOfBirthMillis.hashCode()
        result = 31 * result + childPin.hashCode()
        result = 31 * result + screenTimeBudgetMinutes
        result = 31 * result + (faceImagePath?.hashCode() ?: 0)
        result = 31 * result + (faceEmbedding?.contentHashCode() ?: 0)
        result = 31 * result + fingerprintEnrolled.hashCode()
        result = 31 * result + isAdmin.hashCode()
        result = 31 * result + isSaving.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + isSaved.hashCode()
        result = 31 * result + restrictedPackages.hashCode()
        return result
    }
}

@HiltViewModel
class EnrollmentViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val pinManager: PinManager,
    private val recognitionPipeline: FaceRecognitionPipeline,
    private val embeddingExtractor: FaceEmbeddingExtractor,
    private val ageCalculator: AgeCalculator,
    val adminAuth: AdminAuth
) : ViewModel() {

    companion object {
        private const val TAG = "EnrollmentViewModel"
    }

    private val _uiState = MutableStateFlow(EnrollmentUiState())
    val uiState: StateFlow<EnrollmentUiState> = _uiState.asStateFlow()

    fun loadProfileForEdit(profileId: String?) {
        if (profileId.isNullOrBlank()) return
        viewModelScope.launch {
            val existing = profileRepository.getProfileById(profileId)
            if (existing != null) {
                _uiState.value = _uiState.value.copy(
                    profileId = existing.id,
                    isEditMode = true,
                    name = existing.name,
                    role = existing.role,
                    dateOfBirthMillis = existing.dateOfBirth,
                    childPin = "",
                    screenTimeBudgetMinutes = existing.screenTimeBudgetMinutes,
                    faceImagePath = existing.faceImagePath,
                    faceEmbedding = existing.faceEmbedding,
                    fingerprintEnrolled = existing.fingerprintEnrolled,
                    isAdmin = existing.isAdmin,
                    restrictedPackages = existing.restrictedPackages
                )
            }
        }
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name, errorMessage = null)
    }

    fun onRoleChanged(role: Role) {
        _uiState.value = _uiState.value.copy(
            role = role,
            screenTimeBudgetMinutes = if (role == Role.ADULT) 999 else 60
        )
    }

    fun onDobChanged(dobMillis: Long) {
        val suggestedRole = ageCalculator.suggestedRole(dobMillis)
        _uiState.value = _uiState.value.copy(
            dateOfBirthMillis = dobMillis,
            role = suggestedRole,
            screenTimeBudgetMinutes = if (suggestedRole == Role.ADULT) 999 else 60
        )
    }

    fun onPinChanged(pin: String) {
        if (pin.length <= 8 && pin.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(childPin = pin, errorMessage = null)
        }
    }

    fun onBudgetChanged(budgetMinutes: Int) {
        _uiState.value = _uiState.value.copy(screenTimeBudgetMinutes = budgetMinutes)
    }

    fun onFacePhotoCaptured(path: String, face: Face?) {
        if (face == null) {
            Log.w(TAG, "No face detected during capture call")
            _uiState.value = _uiState.value.copy(errorMessage = "No face in frame — please look directly at camera")
            return
        }

        // Quality check
        val quality = recognitionPipeline.checkQuality(face, 640, 480) // Assuming standard preview size for now
        if (quality !is FaceRecognitionPipeline.FaceQuality.Good) {
            val msg = when (quality) {
                is FaceRecognitionPipeline.FaceQuality.TooFar -> quality.message
                is FaceRecognitionPipeline.FaceQuality.PoorPose -> quality.message
                is FaceRecognitionPipeline.FaceQuality.LowConfidence -> quality.message
                else -> "Poor image quality"
            }
            _uiState.value = _uiState.value.copy(errorMessage = msg)
            return
        }

        val embedding = recognitionPipeline.getEmbedding(face)
        val newSamples = _uiState.value.capturedSamples + embedding

        Log.i(
            TAG,
            "Sample ${newSamples.size}/${_uiState.value.requiredSamples} captured for ${_uiState.value.name}"
        )

        if (newSamples.size >= _uiState.value.requiredSamples) {
            val averaged = embeddingExtractor.average(newSamples)
            _uiState.value = _uiState.value.copy(
                faceImagePath = path,
                capturedSamples = newSamples,
                faceEmbedding = averaged,
                errorMessage = null
            )
        } else {
            _uiState.value = _uiState.value.copy(
                capturedSamples = newSamples,
                errorMessage = "Keep looking at the camera (${newSamples.size}/${_uiState.value.requiredSamples})"
            )
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        Log.d(
            TAG,
            "Saving profile: ${state.name}, role: ${state.role}, faceEnrolled: ${state.faceEmbedding != null}"
        )

        if (state.name.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter a profile name")
            return
        }

        if (state.faceEmbedding == null) {
            _uiState.value = state.copy(errorMessage = "Please capture a face photo to enroll face recognition")
            return
        }

        if (state.role == Role.CHILD && !state.isEditMode && !pinManager.isValidPin(state.childPin)) {
            _uiState.value = state.copy(errorMessage = "Child PIN must be 4 to 8 digits")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            try {
                val pinHash = if (state.role == Role.CHILD) {
                    if (state.childPin.isNotBlank()) pinManager.hashPin(state.childPin) else null
                } else null

                val profile = Profile(
                    id = state.profileId,
                    name = state.name.trim(),
                    dateOfBirth = state.dateOfBirthMillis,
                    role = state.role,
                    faceImagePath = state.faceImagePath,
                    faceEmbedding = state.faceEmbedding,
                    fingerprintEnrolled = false,
                    childPinHash = pinHash,
                    isAdmin = state.isAdmin || state.role == Role.ADULT,
                    screenTimeBudgetMinutes = state.screenTimeBudgetMinutes,
                    remainingScreenTimeMinutes = state.screenTimeBudgetMinutes,
                    restrictedPackages = state.restrictedPackages
                )

                if (state.isEditMode) {
                    profileRepository.updateProfile(profile)
                    Log.i(TAG, "Profile '${profile.name}' (ID: ${profile.id}) updated in Room DB.")
                } else {
                    profileRepository.insertProfile(profile)
                    Log.i(TAG, "New Profile '${profile.name}' (ID: ${profile.id}) successfully stored in Room DB with embedding size ${profile.faceEmbedding?.size ?: 0}.")
                }

                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save profile", e)
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = e.message ?: "Failed to save profile")
            }
        }
    }
}
