package com.enkiba.app.ui.users

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/** Création d'un vendeur, ou modification si [EXTRA_USER_ID] est fourni (admin). */
class UserFormActivity : ComponentActivity() {

    private val viewModel: UserFormViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn || !session.isAdmin) {
            finish(); return
        }
        viewModel.start(intent.getStringExtra(EXTRA_USER_ID))
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.savedMessage) {
                    state.savedMessage?.let {
                        setResult(Activity.RESULT_OK, Intent().putExtra(RESULT_MESSAGE, it))
                        finish()
                    }
                }
                UserFormScreen(state = state, onChange = viewModel::update, onSave = viewModel::save, onBack = ::finish)
            }
        }
    }

    companion object {
        const val EXTRA_USER_ID = "user_id"
        const val RESULT_MESSAGE = "message"
    }
}
