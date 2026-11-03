package com.enkiba.app.ui.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.data.local.ShopStore
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/** Discussion de la boutique : un canal pour toute l'équipe, rafraîchi toutes les 5 secondes. */
class ChatActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        val shopName = ShopStore.get(this, session.shopId).name
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ChatScreen(
                    state = state,
                    shopName = shopName,
                    actions = ChatActions(
                        onBack = ::finish,
                        onDraft = viewModel::onDraft,
                        onSend = viewModel::send,
                        onLongPress = viewModel::askDelete,
                        onConfirmDelete = viewModel::confirmDelete,
                        onCancelDelete = viewModel::cancelDelete,
                    ),
                )
            }
        }
    }

    // Interrogation du serveur seulement quand l'écran est visible
    override fun onStart() {
        super.onStart()
        viewModel.startPolling()
    }

    override fun onStop() {
        viewModel.stopPolling()
        super.onStop()
    }
}
