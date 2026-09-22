package com.smartguard.prototype.ui.viewmodels

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.face.Face
import com.smartguard.prototype.accessibility.AccessibilityStateMonitor
import com.smartguard.prototype.identity.FaceEmbeddingExtractor
import com.smartguard.prototype.identity.FaceRecognitionPipeline
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.ProfileRepository
import com.smartguard.prototype.profile.Role
import com.smartguard.prototype.security.AdminAuth
import com.smartguard.prototype.security.PinManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FirstRunSetupUiState(
    val name: String = "",
    val dateOfBirthMillis: Long = System.currentTimeMillis() - (30L * 365 * 24 * 3600 * 1000), // ~30 yrs default
    val adminPin: String = "",
    val faceImagePath: String? = null,
    val faceEmbedding: FloatArray? = null,
    val capturedSamples: List<FloatArray> = emptyList(),
    val requiredSamples: Int = 5,
    val fingerprintEnrolled: Boolean = false,
    val isBatteryOptimized: Boolean = true,
    val isAccessibilityEnabled: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val isCompleted: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FirstRunSetupUiState
        if (name != other.name) return false
        if (dateOfBirthMillis != other.dateOfBirthMillis) return false
        if (adminPin != other.adminPin) return false
        if (faceImagePath != other.faceImagePath) return false
        if (faceEmbedding != null) {
            if (other.faceEmbedding == null) return false
            if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false
        } else if (other.faceEmbedding != null) return false
        if (fingerprintEnrolled != other.fingerprintEnrolled) return false
        if (isSaving != other.isSaving) return false
        if (errorMessage != other.errorMessage) return false
        if (isCompleted != other.isCompleted) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + dateOfBirthMillis.hashCode()
        result = 31 * result + adminPin.hashCode()
        result = 31 * result + (faceImagePath?.hashCode() ?: 0)
        result = 31 * result + (faceEmbedding?.contentHashCode() ?: 0)
        result = 31 * result + fingerprintEnrolled.hashCode()
        result = 31 * result + isSaving.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + isCompleted.hashCode()
        return result
    }
}

@HiltViewModel
class FirstRunSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val pinManager: PinManager,
    private val recognitionPipeline: FaceRecognitionPipeline,
    private val embeddingExtractor: FaceEmbeddingExtractor,
    private val accessibilityMonitor: AccessibilityStateMonitor,
    val adminAuth: AdminAuth
) : ViewModel() {

    companion object {
        private const val TAG = "FirstRunSetupVM"
    }

    private val _uiState = MutableStateFlow(FirstRunSetupUiState())
    val uiState: StateFlow<FirstRunSetupUiState> = _uiState.asStateFlow()

    init {
        checkBatteryOptimization()
        checkAccessibility()
    }

    fun checkAccessibility() {
        accessibilityMonitor.refresh()
        _uiState.value = _uiState.value.copy(isAccessibilityEnabled = accessibilityMonitor.isServiceEnabled.value)
    }

    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun checkBatteryOptimization() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isIgnoring = pm.isIgnoringBatteryOptimizations(context.packageName)
        _uiState.value = _uiState.value.copy(isBatteryOptimized = !isIgnoring)
    }

    fun requestIgnoreBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not launch battery optimization settings: ${e.message}")
        }
    }

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name, errorMessage = null)
    }

    fun onDobChanged(dobMillis: Long) {
        _uiState.value = _uiState.value.copy(dateOfBirthMillis = dobMillis)
    }

    fun onPinChanged(pin: String) {
        if (pin.length <= 8 && pin.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(adminPin = pin, errorMessage = null)
        }
    }

    fun onFacePhotoCaptured(path: String, face: Face?) {
        if (face == null) {
            Log.w(TAG, "No face detected during capture call")
            _uiState.value = _uiState.value.copy(errorMessage = "No face in frame — please look directly at camera")
            return
        }

        // Quality check
        val quality = recognitionPipeline.checkQuality(face, 640, 480)
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

        Log.i(TAG, "Admin Sample ${newSamples.size}/${_uiState.value.requiredSamples} captured")

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
                errorMessage = "Hold steady... (${newSamples.size}/${_uiState.value.requiredSamples})"
            )
        }
    }

    fun saveAdminProfile() {
        val state = _uiState.value
        Log.d(TAG, "Saving Admin: ${state.name}, faceEnrolled: ${state.faceEmbedding != null}")

        if (state.name.isBlank()) {
            _uiState.value = state.copy(errorMessage = "Please enter your name")
            return
        }
        if (!pinManager.isValidPin(state.adminPin)) {
            _uiState.value = state.copy(errorMessage = "PIN must be between 4 and 8 digits")
            return
        }
        if (state.faceEmbedding == null) {
            _uiState.value = state.copy(errorMessage = "Please capture a face photo to complete setup")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                val adminProfile = Profile(
                    id = ProfileRepository.SEED_ADULT_ID,
                    name = state.name.trim(),
                    dateOfBirth = state.dateOfBirthMillis,
                    role = Role.ADULT,
                    faceImagePath = state.faceImagePath,
                    faceEmbedding = state.faceEmbedding,
                    fingerprintEnrolled = false,
                    childPinHash = pinManager.hashPin(state.adminPin),
                    isAdmin = true,
                    screenTimeBudgetMinutes = 999,
                    remainingScreenTimeMinutes = 999,
                    restrictedPackages = emptyList() // Admin has full access anyway, but good to be explicit
                )
                profileRepository.insertProfile(adminProfile)
                Log.i(TAG, "Initial Admin profile '${adminProfile.name}' saved successfully with face embedding vector (size: ${adminProfile.faceEmbedding?.size}).")
                _uiState.value = _uiState.value.copy(isSaving = false, isCompleted = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save admin profile", e)
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = e.message ?: "Failed to save profile")
            }
        }
    }
}
