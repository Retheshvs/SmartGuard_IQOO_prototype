package com.smartguard.prototype

import android.app.Application
import android.util.Log
import com.smartguard.prototype.di.ApplicationScope
import com.smartguard.prototype.profile.ProfileRepository
import com.smartguard.prototype.security.ScreenUnlockManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class SmartGuardApp : Application() {

    companion object {
        private const val TAG = "SmartGuardApp"
    }

    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var screenUnlockManager: ScreenUnlockManager
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        logDatabaseState()
        screenUnlockManager.registerReceiver()
    }

    private fun logDatabaseState() {
        applicationScope.launch {
            try {
                val profiles = profileRepository.getAllProfiles()
                Log.i(TAG, "Database Initialized. Total profiles: ${profiles.size}")
                profiles.forEach { p ->
                    Log.i(TAG, " -> Profile: ${p.name}, Role: ${p.role}, isAdmin: ${p.isAdmin}, hasEmbedding: ${p.faceEmbedding != null}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log database state", e)
            }
        }
    }
}
