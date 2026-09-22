package com.smartguard.prototype.profile

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY isAdmin DESC, name ASC")
    fun observeAllProfiles(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY isAdmin DESC, name ASC")
    suspend fun getAllProfiles(): List<Profile>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: String): Profile?

    @Query("SELECT * FROM profiles WHERE isAdmin = 1 LIMIT 1")
    suspend fun getAdminProfile(): Profile?

    /** Returns the first non-ADULT profile (CHILD or TEEN). */
    @Query("SELECT * FROM profiles WHERE role != 'ADULT' LIMIT 1")
    suspend fun getChildProfile(): Profile?

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProfile(profile: Profile)

    @Update
    suspend fun updateProfile(profile: Profile)

    @Delete
    suspend fun deleteProfile(profile: Profile)

    @Query("UPDATE profiles SET remainingScreenTimeMinutes = :remaining WHERE id = :profileId")
    suspend fun updateRemainingScreenTime(profileId: String, remaining: Int)

    @Query("UPDATE profiles SET faceEmbedding = :embedding WHERE id = :profileId")
    suspend fun updateFaceEmbedding(profileId: String, embedding: FloatArray)

    @Query("UPDATE profiles SET restrictedPackages = :packages WHERE id = :profileId")
    suspend fun updateRestrictedPackages(profileId: String, packages: List<String>)

    @Query("SELECT * FROM profiles WHERE role = 'CHILD' OR role = 'TEEN'")
    suspend fun getChildProfiles(): List<Profile>

    @Query("SELECT * FROM profiles WHERE role = 'CHILD' OR role = 'TEEN'")
    fun observeChildProfiles(): Flow<List<Profile>>
}
