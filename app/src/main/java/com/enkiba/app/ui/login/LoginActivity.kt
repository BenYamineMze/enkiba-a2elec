package com.enkiba.app.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.activities.AcceuilActivity
import com.enkiba.app.ui.theme.EnkibaTheme

/**
 * Point d'entrée de l'application. Session existante : on va directement à l'accueil
 * (l'application s'ouvre aussi hors ligne) ; sinon, écran de connexion.
 */
class LoginActivity : ComponentActivity() {

    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (viewModel.state.value.loggedIn) {
            openHome()
            return
        }
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.loggedIn) { if (state.loggedIn) openHome() }
                LoginScreen(
                    state = state,
                    onEmailChange = viewModel::onEmailChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onLogin = viewModel::login,
                )
            }
        }
    }

    // La synchronisation est lancée par l'accueil
    private fun openHome() {
        startActivity(Intent(this, AcceuilActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}
