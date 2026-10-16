package com.enkiba.app.ui.users

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/** Équipe de la boutique (Compose, admin) : liste, activation, ajout et modification des vendeurs. */
class TeamActivity : ComponentActivity() {

    private val viewModel: TeamViewModel by viewModels()

    // Retour du formulaire : message à afficher (la liste se recharge dans onResume)
    private val formLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringExtra(UserFormActivity.RESULT_MESSAGE)?.let(viewModel::showMessage)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        if (!session.isAdmin) {
            finish(); return
        }
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                TeamScreen(
                    state = state,
                    myUserId = session.userId,
                    actions = TeamActions(
                        onBack = ::finish,
                        onAdd = { formLauncher.launch(Intent(this, UserFormActivity::class.java)) },
                        onEdit = { m -> formLauncher.launch(Intent(this, UserFormActivity::class.java).putExtra(UserFormActivity.EXTRA_USER_ID, m.userId)) },
                        onToggle = viewModel::onToggle,
                        onConfirmDeactivate = viewModel::confirmDeactivate,
                        onCancelDeactivate = viewModel::cancelDeactivate,
                        onRefresh = { viewModel.load(refresh = true) },
                        onRetry = { viewModel.load() },
                        onMessageShown = viewModel::consumeMessage,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}
