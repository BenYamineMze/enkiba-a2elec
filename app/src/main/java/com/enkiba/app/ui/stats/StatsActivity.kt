package com.enkiba.app.ui.stats

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.ui.navigation.AppNavigator
import com.enkiba.app.ui.products.ProductDetailActivity
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/** Statistiques (Compose, admin) : encaissé du mois et par jour, état du stock. */
class StatsActivity : ComponentActivity() {

    private val viewModel: StatsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        // Réservé à l'admin, comme l'onglet de la barre de navigation
        if (!session.isAdmin) {
            finish(); return
        }
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                StatsScreen(
                    state = state,
                    actions = StatsActions(
                        onMonth = viewModel::selectMonth,
                        onProduct = { p ->
                            startActivity(Intent(this, ProductDetailActivity::class.java).putExtra(ProductDetailActivity.EXTRA_PRODUCT_ID, p.id))
                        },
                        onNavigate = { tab -> AppNavigator.open(this, tab, isAdmin = true) },
                        onNewSale = { AppNavigator.newSale(this) },
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }
}
