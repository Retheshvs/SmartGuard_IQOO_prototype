package com.smartguard.prototype.profile

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single enrolled user profile.
 *
 * Identity is established through [faceEmbedding] — a real float vector produced
 * by the TFLite MobileFaceNet model (or the geometric fallback extractor).
 * No face photo is stored — only the embedding vector.
 *
 * [role] is NOT cached permanently; it is re-computed from [dateOfBirth] via
 * [AgeCalculator.computeRole] on every read, so it stays accurate as a child ages.
 */
@Entity(tableName = "profiles")
data class Profile(
    @PrimaryKey val id: String,
    val name: String,
    /** Epoch milliseconds UTC */
    val dateOfBirth: Long,
    /**
     * Stored for database indexing; ALWAYS re-derive the live role via AgeCalculator
     * — do not trust this field alone for access control decisions.
     */
    val role: Role,
    /** Absolute path to captured face photo — used for UI only, not identity. */
    val faceImagePath: String?,
    /**
     * Face embedding vector from TFLite MobileFaceNet (128-dim) or geometric fallback.
     * Null until the user completes the EnrollmentScreen face-capture step.
     */
    val faceEmbedding: FloatArray?,
    /** True if the user has enrolled their fingerprint via system BiometricPrompt. */
    val fingerprintEnrolled: Boolean,
    /** SHA-256+salt hash of the child's PIN. Null for ADULT profiles. */
    val childPinHash: String?,
    val isAdmin: Boolean,
    /** Daily screen-time budget in minutes; set by parent via SettingsScreen. */
    val screenTimeBudgetMinutes: Int,
    /**
     * Remaining screen-time minutes persisted to Room on every 5-minute boundary
     * and on session handover. ScreenTimeManager uses an in-memory cache for
     * the live countdown, so this is the last-known durable value.
     */
    val remainingScreenTimeMinutes: Int,
    /**
     * Package names of apps that the AccessibilityService will block
     * when this profile is active. e.g. "com.google.android.youtube"
     * Set by parent via RestrictedAppsScreen.
     */
    val restrictedPackages: List<String> = emptyList()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Profile
        if (id != other.id) return false
        if (name != other.name) return false
        if (dateOfBirth != other.dateOfBirth) return false
        if (role != other.role) return false
        if (faceImagePath != other.faceImagePath) return false
        if (faceEmbedding != null) {
            if (other.faceEmbedding == null) return false
            if (!faceEmbedding.contentEquals(other.faceEmbedding)) return false
        } else if (other.faceEmbedding != null) return false
        if (fingerprintEnrolled != other.fingerprintEnrolled) return false
        if (childPinHash != other.childPinHash) return false
        if (isAdmin != other.isAdmin) return false
        if (screenTimeBudgetMinutes != other.screenTimeBudgetMinutes) return false
        if (remainingScreenTimeMinutes != other.remainingScreenTimeMinutes) return false
        if (!restrictedPackages.contentEquals(other.restrictedPackages)) return false
        return true
    }

    private fun List<String>.contentEquals(other: List<String>): Boolean {
        if (size != other.size) return false
        for (i in indices) {
            if (this[i] != other[i]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + dateOfBirth.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + (faceImagePath?.hashCode() ?: 0)
        result = 31 * result + (faceEmbedding?.contentHashCode() ?: 0)
        result = 31 * result + fingerprintEnrolled.hashCode()
        result = 31 * result + (childPinHash?.hashCode() ?: 0)
        result = 31 * result + isAdmin.hashCode()
        result = 31 * result + screenTimeBudgetMinutes
        result = 31 * result + remainingScreenTimeMinutes
        result = 31 * result + restrictedPackages.hashCode()
        return result
    }
}
