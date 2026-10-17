package com.enkiba.app.ui.products

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.ui.navigation.AppNavigator
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/** Liste des produits (Compose). Extra facultatif [EXTRA_TAB] : onglet à ouvrir. */
class ProductsActivity : ComponentActivity() {

    private val viewModel: ProductsViewModel by viewModels()
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout()
            finish()
            return
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_TAB)?.let { name -> StockTab.entries.firstOrNull { it.name == name }?.let(viewModel::onTabChange) }
        }
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ProductsScreen(
                    state = state,
                    actions = ProductsActions(
                        onQueryChange = viewModel::onQueryChange,
                        onTabChange = viewModel::onTabChange,
                        onRefresh = viewModel::refresh,
                        onOpen = { p -> startActivity(Intent(this, ProductDetailActivity::class.java).putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, p.id)) },
                        onEdit = { p -> startActivity(Intent(this, ProductFormActivity::class.java).putExtra(ProductFormActivity.EXTRA_PRODUCT_ID, p.id)) },
                        onAskDelete = viewModel::askDelete,
                        onConfirmDelete = viewModel::confirmDelete,
                        onCancelDelete = viewModel::cancelDelete,
                        onAdd = { startActivity(Intent(this, ProductFormActivity::class.java)) },
                        onQrList = { startActivity(Intent(this, QrListActivity::class.java)) },
                        onNavigate = { tab -> AppNavigator.open(this, tab, session.isAdmin) },
                        onNewSale = { AppNavigator.newSale(this) },
                        onMessageShown = viewModel::consumeMessage,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Retour d'un ajout, d'une modification ou d'une vente : stock à jour
        viewModel.reload()
    }

    companion object {
        /** Nom d'un [StockTab] (ALL, LOW, OUT). */
        const val EXTRA_TAB = "stock_tab"
    }
}
