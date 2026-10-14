package com.novofitclub.dashboard.core.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gates the dashboard behind the fingerprint/face unlock (falling back to the
 * device PIN) every time the app is opened or comes back from the background.
 * The WP session already stays signed in, so this is purely an extra
 * "someone picked up my phone" lock for sales/cash data, not a login.
 */
class AppLockManager(context: Context) {
    private val prefs = context.getSharedPreferences("novo_lock", Context.MODE_PRIVATE)

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private var promptShowing = false

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun lock() {
        _isUnlocked.value = false
    }

    fun authenticate(activity: FragmentActivity) {
        if (!_isEnabled.value) {
            _isUnlocked.value = true
            return
        }
        if (promptShowing) return

        // No biometrics or screen lock configured on this device at all —
        // don't strand the user behind a lock screen they can never pass.
        val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            _isUnlocked.value = true
            return
        }

        _lastError.value = null
        promptShowing = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    _isUnlocked.value = true
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    _isUnlocked.value = false
                    // Silent on cancel — the "Desbloquear" button lets them retry.
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        _lastError.value = errString.toString()
                    }
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Novo Fit Club")
                .setSubtitle("Confirma tu identidad para ver los datos de Novo Fit Club")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    private companion object {
        const val KEY_ENABLED = "app_lock_enabled"
    }
}
