package com.smartguard.prototype.profile

import android.util.Log
import com.smartguard.prototype.security.PinManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val pinManager: PinManager
) {
    companion object {
        private const val TAG = "ProfileRepository"

        // Stable IDs for seeded test profiles — checked before seeding
        const val SEED_ADULT_ID = "seed_adult_001"
        const val SEED_CHILD_ID = "seed_child_001"
    }

    fun observeAllProfiles(): Flow<List<Profile>> = dao.observeAllProfiles()

    suspend fun getAllProfiles(): List<Profile> = dao.getAllProfiles()

    suspend fun getProfileById(id: String): Profile? = dao.getProfileById(id)

    suspend fun getAdminProfile(): Profile? = dao.getAdminProfile()

    suspend fun getChildProfile(): Profile? = dao.getChildProfile()

    suspend fun getProfileCount(): Int = dao.getProfileCount()

    suspend fun hasAnyProfile(): Boolean = dao.getProfileCount() > 0

    suspend fun insertProfile(profile: Profile) = dao.insertProfile(profile)

    suspend fun updateProfile(profile: Profile) = dao.updateProfile(profile)

    suspend fun deleteProfile(profile: Profile) = dao.deleteProfile(profile)

    suspend fun updateRemainingTime(profileId: String, remaining: Int) {
        dao.updateRemainingScreenTime(profileId, remaining)
    }

    suspend fun updateFaceEmbedding(profileId: String, embedding: FloatArray) {
        dao.updateFaceEmbedding(profileId, embedding)
    }

    suspend fun updateRestrictedPackages(profileId: String, packages: List<String>) {
        dao.updateRestrictedPackages(profileId, packages)
    }

    /**
     * Populates the database with default test profiles if empty.
     * Used for debugging and prototype demonstration.
     */
    suspend fun seedInitialProfilesIfNeeded() {
        if (hasAnyProfile()) {
            Log.d(TAG, "Database already has profiles, skipping seed.")
            return
        }

        Log.i(TAG, "Seeding initial profiles for demonstration mode...")

        val parent = Profile(
            id = SEED_ADULT_ID,
            name = "Alex (Parent)",
            dateOfBirth = System.currentTimeMillis() - (35L * 365 * 24 * 60 * 60 * 1000), // ~35 years old
            role = Role.ADULT,
            faceImagePath = null,
            faceEmbedding = null,
            fingerprintEnrolled = false,
            childPinHash = null,
            isAdmin = true,
            screenTimeBudgetMinutes = 999,
            remainingScreenTimeMinutes = 999
        )

        val child = Profile(
            id = SEED_CHILD_ID,
            name = "Jamie (Child)",
            dateOfBirth = System.currentTimeMillis() - (10L * 365 * 24 * 60 * 60 * 1000), // ~10 years old
            role = Role.CHILD,
            faceImagePath = null,
            faceEmbedding = null,
            fingerprintEnrolled = false,
            childPinHash = pinManager.hashPin("1234"),
            isAdmin = false,
            screenTimeBudgetMinutes = 60,
            remainingScreenTimeMinutes = 60,
            restrictedPackages = listOf(
                "com.google.android.youtube",
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.twitter.android",
                "com.android.vending"
            )
        )

        dao.insertProfile(parent)
        dao.insertProfile(child)
        Log.i(TAG, "Seeded initial test profiles: Alex and Jamie")
    }
}
