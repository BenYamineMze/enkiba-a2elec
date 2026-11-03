package com.enkiba.app.ui.lock

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/** « Code PIN » (menu de l'avatar) : activer, changer, désactiver, empreinte. */
class PinSettingsActivity : ComponentActivity() {

    private val viewModel: PinSettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout()
            finish()
            return
        }
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                BackHandler(onBack = viewModel::back)
                LaunchedEffect(state.message) {
                    state.message?.let {
                        Toast.makeText(this@PinSettingsActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.messageShown()
                    }
                }
                LaunchedEffect(state.closed) { if (state.closed) finish() }
                LaunchedEffect(state.loggedOut) {
                    if (state.loggedOut) {
                        Toast.makeText(this@PinSettingsActivity, "Trop d'essais : reconnectez-vous avec votre mot de passe.", Toast.LENGTH_LONG).show()
                        session.logout()
                    }
                }
                PinSettingsScreen(
                    state = state,
                    onBack = viewModel::back,
                    onDigit = viewModel::onDigit,
                    onDelete = viewModel::onDelete,
                    onBiometric = viewModel::setBiometric,
                    onChange = { viewModel.start(PinAction.CHANGE) },
                    onDisable = { viewModel.start(PinAction.DISABLE) },
                )
            }
        }
    }
}
