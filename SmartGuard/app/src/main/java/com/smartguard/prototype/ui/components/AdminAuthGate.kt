package com.smartguard.prototype.ui.components

import androidx.biometric.BiometricManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.smartguard.prototype.security.AdminAuth
import com.smartguard.prototype.security.AuthResult
import kotlinx.coroutines.launch

private sealed class GateState {
    object Idle                  : GateState()
    object Authenticating        : GateState()
    object Success               : GateState()
    object Cancelled             : GateState()
    object HardwareUnavailable   : GateState()
    object NoBiometricEnrolled   : GateState()
    data class Error(val msg: String) : GateState()
}

/**
 * Reusable admin authentication composable.
 *
 * On first composition, immediately shows the system [BiometricPrompt].
 * - On success  → calls [onSuccess]
 * - On cancel   → calls [onCancel]
 * - On failure  → calls [onFailure]
 *
 * Hardware-unavailable and no-biometrics-enrolled cases show an actionable
 * error card with a "Go Back" button rather than a frozen or blank screen.
 *
 * This is NOT a navigation destination — it is embedded directly in the caller
 * composable to intercept access before protected content is shown.
 */
@Composable
fun AdminAuthGate(
    adminAuth: AdminAuth,
    title: String = "Admin Verification Required",
    subtitle: String = "Authenticate to continue",
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
    onCancel: () -> Unit = onFailure
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    var gateState by remember { mutableStateOf<GateState>(GateState.Idle) }

    suspend fun doAuth() {
        if (activity == null) {
            gateState = GateState.Error("Cannot display biometric prompt")
            return
        }
        val biometricManager = BiometricManager.from(context)
        val canAuth = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            gateState = when (canAuth) {
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> GateState.HardwareUnavailable
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED  -> GateState.NoBiometricEnrolled
                else -> GateState.HardwareUnavailable
            }
            return
        }
        gateState = GateState.Authenticating
        when (val result = adminAuth.authenticate(activity, title, subtitle)) {
            is AuthResult.Success           -> { gateState = GateState.Success; onSuccess() }
            is AuthResult.Cancelled         -> { gateState = GateState.Cancelled; onCancel() }
            is AuthResult.HardwareUnavailable -> { gateState = GateState.HardwareUnavailable }
            is AuthResult.NoBiometricEnrolled -> { gateState = GateState.NoBiometricEnrolled }
            is AuthResult.Failure           -> { gateState = GateState.Error(result.message); onFailure() }
        }
    }

    LaunchedEffect(Unit) { doAuth() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(title,   style = MaterialTheme.typography.headlineSmall,  color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium,     color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f), textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(8.dp))

            when (val s = gateState) {
                GateState.Idle, GateState.Authenticating -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text("Waiting for biometric…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
                GateState.Success -> {
                    Icon(Icons.Default.Fingerprint, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Verified", color = MaterialTheme.colorScheme.primary)
                }
                GateState.Cancelled -> {
                    Text("Authentication cancelled", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                    Button(onClick = { scope.launch { doAuth() } }) { Text("Try Again") }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
                GateState.HardwareUnavailable -> {
                    ErrorCard("Biometric hardware unavailable", "Please ensure your device has a fingerprint sensor and it is enabled.")
                    OutlinedButton(onClick = onFailure) { Text("Go Back") }
                }
                GateState.NoBiometricEnrolled -> {
                    ErrorCard("No Biometrics Enrolled", "No biometric credentials enrolled on this device — add one in Settings")
                    OutlinedButton(onClick = onFailure) { Text("Go Back") }
                }
                is GateState.Error -> {
                    ErrorCard("Authentication error", s.msg)
                    OutlinedButton(onClick = onFailure) { Text("Go Back") }
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleSmall,  color = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.height(4.dp))
            Text(body,  style = MaterialTheme.typography.bodySmall,   color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f), textAlign = TextAlign.Center)
        }
    }
}
