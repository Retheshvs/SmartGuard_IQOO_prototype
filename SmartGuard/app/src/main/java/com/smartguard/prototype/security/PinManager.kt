package com.smartguard.prototype.security

import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinManager @Inject constructor() {

    /**
     * Returns a salted SHA-256 hash of [pin].
     * Stored in [com.smartguard.prototype.profile.Profile.childPinHash].
     */
    fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val salted = "smartguard_v1_$pin"
        return digest.digest(salted.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Returns true if [pin] produces the same hash as [storedHash]. */
    fun verifyPin(pin: String, storedHash: String): Boolean = hashPin(pin) == storedHash

    /** Validates format: 4–8 numeric digits. */
    fun isValidPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it.isDigit() }
}
