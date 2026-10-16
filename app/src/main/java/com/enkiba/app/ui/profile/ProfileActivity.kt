package com.enkiba.app.ui.profile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/** « Mon profil » : prénom, nom et téléphone de l'utilisateur connecté. */
class ProfileActivity : ComponentActivity() {

    private val viewModel: ProfileViewModel by viewModels()

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
                ProfileScreen(
                    state = state,
                    onBack = ::finish,
                    onFirstNameChange = viewModel::onFirstNameChange,
                    onLastNameChange = viewModel::onLastNameChange,
                    onPhoneChange = viewModel::onPhoneChange,
                    onSave = viewModel::save,
                )
            }
        }
    }
}
