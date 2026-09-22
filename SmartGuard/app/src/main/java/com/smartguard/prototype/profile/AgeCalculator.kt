package com.smartguard.prototype.profile

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgeCalculator @Inject constructor() {

    /** Returns age in full years from an epoch-millis date of birth. */
    fun calculateAge(dateOfBirthMillis: Long): Int {
        val birthDate = Instant.ofEpochMilli(dateOfBirthMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        val today = LocalDate.now()
        return ChronoUnit.YEARS.between(birthDate, today).toInt().coerceAtLeast(0)
    }

    /**
     * Suggests a [Role] based on age:
     * - < 13  → CHILD
     * - 13–17 → TEEN
     * - 18+   → ADULT
     */
    fun suggestedRole(dateOfBirthMillis: Long): Role {
        return when (val age = calculateAge(dateOfBirthMillis)) {
            in 0..12  -> Role.CHILD
            in 13..17 -> Role.TEEN
            else      -> Role.ADULT
        }
    }

    /** Alias for [suggestedRole] used by PolicyEngine. */
    fun computeRole(dateOfBirthMillis: Long): Role = suggestedRole(dateOfBirthMillis)
}
