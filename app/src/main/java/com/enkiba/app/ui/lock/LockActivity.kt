package com.enkiba.app.ui.lock

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.security.AppLock
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/**
 * Écran de verrouillage, posé par-dessus l'écran en cours (EnkibaApp). Le retour met l'app
 * en arrière-plan : on ne peut pas passer sans le code, l'empreinte ou une reconnexion.
 * FragmentActivity : nécessaire à la demande d'empreinte (BiometricPrompt).
 */
class LockActivity : FragmentActivity() {

    private val viewModel: LockViewModel by viewModels()
    private lateinit var session: SessionManager
    private var biometricAsked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        AppLock.screenShown = true
        // Verrou levé entre-temps (ou session fermée) : rien à demander
        if (!AppLock.isLocked(this) || !AppLock.isEnabled(session)) {
            AppLock.unlock()
            finish()
            return
        }
        biometricAsked = savedInstanceState?.getBoolean(KEY_ASKED) ?: false
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })
        enableEdgeToEdge()
        val biometric = session.isBiometricEnabled && Biometrics.available(this)
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.unlocked) { if (state.unlocked) finishUnlocked() }
                LaunchedEffect(state.loggedOut) { if (state.loggedOut) logout(state.tooManyAttempts) }
                LockScreen(
                    state = state,
                    firstName = session.firstName,
                    onDigit = viewModel::onDigit,
                    onDelete = viewModel::onDelete,
                    onBiometric = if (biometric) ::askBiometric else null,
                    onForgot = viewModel::forgot,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Demande d'empreinte automatique, une fois par verrouillage
        if (!biometricAsked && session.isBiometricEnabled && Biometrics.available(this)) {
            biometricAsked = true
            askBiometric()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ASKED, biometricAsked)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) AppLock.screenShown = false
    }

    private fun askBiometric() = Biometrics.prompt(this, "Touchez le capteur pour continuer") { viewModel.onBiometricSuccess() }

    private fun finishUnlocked() {
        AppLock.unlock()
        finish()
        @Suppress("DEPRECATION") overridePendingTransition(0, android.R.anim.fade_out)
    }

    /** Trop d'erreurs ou code oublié : reconnexion par mot de passe (les données locales restent). */
    private fun logout(tooManyAttempts: Boolean) {
        if (tooManyAttempts) {
            Toast.makeText(this, "Trop d'essais : reconnectez-vous avec votre mot de passe.", Toast.LENGTH_LONG).show()
        }
        AppLock.unlock()
        session.logout()
        finish()
    }

    private companion object {
        const val KEY_ASKED = "biometric_asked"
    }
}
