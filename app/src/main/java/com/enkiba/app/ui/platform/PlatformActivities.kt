package com.enkiba.app.ui.platform

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/** Écrans réservés au super-admin : l'app les masque aux autres, le serveur refuse de toute façon. */
private fun ComponentActivity.requireSuperAdmin(): Boolean {
    val session = SessionManager(this)
    if (!session.isLoggedIn || !session.isSuperAdmin) {
        finish(); return false
    }
    return true
}

/** Liste de toutes les boutiques. */
class ShopsActivity : ComponentActivity() {
    private val viewModel: ShopsViewModel by viewModels()
    private val createLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) viewModel.showMessage("Boutique créée")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSuperAdmin()) return
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ShopsScreen(
                    state = state,
                    onBack = ::finish,
                    onCreate = { createLauncher.launch(Intent(this, CreateShopActivity::class.java)) },
                    onOpen = { s -> startActivity(Intent(this, ShopAdminActivity::class.java).putExtra(ShopAdminActivity.EXTRA_SHOP_ID, s.shopId)) },
                    onRefresh = { viewModel.load(refresh = true) },
                    onRetry = { viewModel.load() },
                    onMessageShown = viewModel::consumeMessage,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }
}

/** Fiche d'une boutique ([EXTRA_SHOP_ID]). */
class ShopAdminActivity : ComponentActivity() {
    private val viewModel: ShopAdminViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSuperAdmin()) return
        val shopId = intent.getStringExtra(EXTRA_SHOP_ID) ?: run { finish(); return }
        if (savedInstanceState == null) viewModel.load(shopId)
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ShopAdminScreen(
                    state = state,
                    onBack = ::finish,
                    onChange = viewModel::update,
                    onSave = viewModel::save,
                    onAskSuspend = viewModel::askSuspend,
                    onConfirmSuspend = viewModel::toggleSuspension,
                    onCancelSuspend = viewModel::cancelSuspend,
                    onMessageShown = viewModel::consumeMessage,
                )
            }
        }
    }

    companion object {
        const val EXTRA_SHOP_ID = "shop_id"
    }
}

/** Création d'une boutique et de son administrateur. */
class CreateShopActivity : ComponentActivity() {
    private val viewModel: CreateShopViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireSuperAdmin()) return
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.created) {
                    if (state.created) { setResult(Activity.RESULT_OK); finish() }
                }
                CreateShopScreen(state, viewModel::updateShop, viewModel::updateAdmin, viewModel::save, onBack = ::finish)
            }
        }
    }
}
