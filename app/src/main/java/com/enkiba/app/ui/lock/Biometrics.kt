package com.enkiba.app.ui.lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Empreinte ou visage (classe « faible » : suffit pour déverrouiller l'interface, sans clé chiffrée). */
object Biometrics {

    fun available(context: Context) =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Affiche la demande système ; [onSuccess] seulement si le doigt ou le visage est reconnu. */
    fun prompt(activity: FragmentActivity, subtitle: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Déverrouiller Enkiba")
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .setNegativeButtonText("Utiliser le code")
                .build(),
        )
    }
}
