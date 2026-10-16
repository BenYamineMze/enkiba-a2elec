package com.enkiba.app.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.BuildConfig
import com.enkiba.app.activities.HistoryActivity
import com.enkiba.app.activities.ProductListActivity
import com.enkiba.app.activities.SaleDetailsActivity
import com.enkiba.app.activities.StatisticsActivity
import com.enkiba.app.activities.UserManagementActivity
import com.enkiba.app.ui.profile.ProfileActivity
import com.enkiba.app.ui.sale.SaleActivity
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/**
 * Accueil (Compose). Les écrans pas encore migrés s'ouvrent dans leur version XML.
 * Une synchronisation est lancée à l'ouverture.
 */
class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
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
        if (savedInstanceState == null) viewModel.sync()

        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    actions = HomeActions(
                        onSync = viewModel::sync,
                        onOpenTab = ::openTab,
                        onNewSale = { open(SaleActivity::class.java) },
                        onTodaySales = { openHistory(HistoryActivity.TAB_SALES) },
                        onLowStock = { open(ProductListActivity::class.java) },
                        onOpenQuotes = { openHistory(HistoryActivity.TAB_QUOTES) },
                        onStockValue = {
                            if (state.isAdmin) open(StatisticsActivity::class.java) else open(ProductListActivity::class.java)
                        },
                        onAllSales = { openHistory(HistoryActivity.TAB_SALES) },
                        onOpenSale = { sale ->
                            startActivity(Intent(this, SaleDetailsActivity::class.java).putExtra("sale_id", sale.id))
                        },
                        onManageUsers = { open(UserManagementActivity::class.java) },
                        onProfile = { open(ProfileActivity::class.java) },
                        // Le catalogue n'existe que dans le build debug (src/debug)
                        onOpenDesignCatalog = if (BuildConfig.DEBUG) {
                            { startActivity(Intent().setClassName(this, "com.enkiba.app.ui.catalog.DesignCatalogActivity")) }
                        } else null,
                        onLogout = { session.logout() },
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Les chiffres changent au retour d'une vente, d'un retour, d'une synchro…
        if (session.isLoggedIn) viewModel.refresh() else session.logout()
    }

    private fun openTab(tab: HomeTab) {
        when (tab) {
            HomeTab.HOME -> Unit
            HomeTab.PRODUCTS -> open(ProductListActivity::class.java)
            HomeTab.HISTORY -> openHistory(HistoryActivity.TAB_SALES)
            HomeTab.STATS -> if (session.isAdmin) open(StatisticsActivity::class.java)
        }
    }

    private fun open(target: Class<*>) = startActivity(Intent(this, target))

    private fun openHistory(tab: Int) =
        startActivity(Intent(this, HistoryActivity::class.java).putExtra(HistoryActivity.EXTRA_TAB, tab))
}
