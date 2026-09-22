package com.smartguard.prototype.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class AuthResult {
    object Success : AuthResult()
    data class Failure(val message: String) : AuthResult()
    object Cancelled : AuthResult()
    object HardwareUnavailable : AuthResult()
    object NoBiometricEnrolled : AuthResult()
}

/**
 * Wraps [androidx.biometric.BiometricPrompt] in a suspend function.
 *
 * Supports BIOMETRIC_STRONG | BIOMETRIC_WEAK authenticators.
 * Falls back gracefully when hardware is unavailable or no biometrics enrolled.
 * Never crashes — all error codes are mapped to typed [AuthResult] variants.
 */
@Singleton
class AdminAuth @Inject constructor() {

    /**
     * Shows the system biometric prompt and suspends until the user authenticates,
     * cancels, or an error occurs.
     *
     * Must be called from a coroutine. Safe to cancel — the prompt is dismissed.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Admin Verification",
        subtitle: String = "Verify your identity to continue"
    ): AuthResult {
        val biometricManager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        val canAuth = biometricManager.canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            return when (canAuth) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthResult.NoBiometricEnrolled
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> AuthResult.HardwareUnavailable
                else -> AuthResult.Failure("Biometric authentication unavailable (code $canAuth)")
            }
        }

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val executor = ContextCompat.getMainExecutor(activity)

                val callback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(AuthResult.Success)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!cont.isActive) return
                        val result = when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON -> AuthResult.Cancelled
                            BiometricPrompt.ERROR_HW_UNAVAILABLE,
                            BiometricPrompt.ERROR_HW_NOT_PRESENT  -> AuthResult.HardwareUnavailable
                            BiometricPrompt.ERROR_NO_BIOMETRICS   -> AuthResult.NoBiometricEnrolled
                            else -> AuthResult.Failure(errString.toString())
                        }
                        cont.resume(result)
                    }
                }

                val biometricPrompt = BiometricPrompt(activity, executor, callback)

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText("Cancel")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                cont.invokeOnCancellation {
                    try { biometricPrompt.cancelAuthentication() } catch (_: Exception) {}
                }

                try {
                    biometricPrompt.authenticate(promptInfo)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(AuthResult.Failure(e.message ?: "Unknown error"))
                }
            }
        }
    }
}

