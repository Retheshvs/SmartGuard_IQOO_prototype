package com.smartguard.prototype.ui.viewmodels

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartguard.prototype.profile.Profile
import com.smartguard.prototype.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val isRestricted: Boolean
)

data class AllowedAppsUiState(
    val isLoading: Boolean = true,
    val profile: Profile? = null,
    val budgetMinutes: Int = 60,
    val apps: List<AppInfo> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AllowedAppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AllowedAppsUiState())
    val uiState: StateFlow<AllowedAppsUiState> = _uiState.asStateFlow()

    fun loadProfileAndApps(profileId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val profile = profileRepository.getProfileById(profileId)
            if (profile != null) {
                val apps = getInstalledLauncherApps(profile.restrictedPackages)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    profile = profile,
                    budgetMinutes = profile.screenTimeBudgetMinutes,
                    apps = apps
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Profile not found")
            }
        }
    }

    private suspend fun getInstalledLauncherApps(restrictedPackages: List<String>): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        resolveInfos.map { info ->
            AppInfo(
                name = info.loadLabel(pm).toString(),
                packageName = info.activityInfo.packageName,
                icon = info.loadIcon(pm),
                isRestricted = info.activityInfo.packageName in restrictedPackages
            )
        }.sortedBy { it.name.lowercase() }
    }

    fun toggleApp(packageName: String) {
        val currentApps = _uiState.value.apps
        val updatedApps = currentApps.map {
            if (it.packageName == packageName) it.copy(isRestricted = !it.isRestricted) else it
        }
        _uiState.value = _uiState.value.copy(apps = updatedApps)
    }

    fun updateBudget(minutes: Int) {
        _uiState.value = _uiState.value.copy(budgetMinutes = minutes)
    }

    fun saveChanges() {
        val profile = _uiState.value.profile ?: return
        val restrictedPackages = _uiState.value.apps.filter { it.isRestricted }.map { it.packageName }
        val budget = _uiState.value.budgetMinutes
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                // Update both budget and restricted packages
                val updatedProfile = profile.copy(
                    screenTimeBudgetMinutes = budget,
                    remainingScreenTimeMinutes = budget,
                    restrictedPackages = restrictedPackages
                )
                profileRepository.updateProfile(updatedProfile)
                _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = e.message)
            }
        }
    }
}
