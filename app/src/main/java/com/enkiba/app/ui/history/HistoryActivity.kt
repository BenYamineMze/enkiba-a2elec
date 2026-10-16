package com.enkiba.app.ui.history

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.enkiba.app.ui.navigation.AppNavigator
import com.enkiba.app.ui.quotes.QuoteDetailActivity
import com.enkiba.app.ui.sale.SaleDetailActivity
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * Historique (Compose) : ventes, devis, opérations (admin). Les détails d'une vente
 * et d'un devis s'ouvrent encore dans leur version XML. Extra [EXTRA_TAB] : onglet.
 */
class HistoryActivity : ComponentActivity() {

    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            viewModel.selectTab(HistoryTab.entries.getOrElse(intent.getIntExtra(EXTRA_TAB, TAB_SALES)) { HistoryTab.SALES })
        }
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                HistoryScreen(
                    state = state,
                    actions = HistoryActions(
                        onTab = viewModel::selectTab,
                        onQueryChange = viewModel::onQueryChange,
                        onQuoteFilter = viewModel::onQuoteFilter,
                        onRefresh = viewModel::refresh,
                        onOpenSale = { s -> startActivity(Intent(this, SaleDetailActivity::class.java).putExtra(SaleDetailActivity.EXTRA_SALE_ID, s.id)) },
                        onOpenQuote = { q -> startActivity(Intent(this, QuoteDetailActivity::class.java).putExtra(QuoteDetailActivity.EXTRA_QUOTE_ID, q.id)) },
                        onExport = ::export,
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
        viewModel.reload()
    }

    /** Export PDF de la liste affichée (ventes ou opérations). */
    private fun export() {
        val s = viewModel.state.value
        lifecycleScope.launch {
            viewModel.setExporting(true)
            val ok = when (s.tab) {
                HistoryTab.SALES -> s.visibleSales.isNotEmpty() && viewModel.repositoryForPdf.exportSales(this@HistoryActivity, s.visibleSales)
                HistoryTab.OPERATIONS -> s.visibleOperations.isNotEmpty() && viewModel.repositoryForPdf.exportOperations(this@HistoryActivity, s.visibleOperations)
                HistoryTab.QUOTES -> false
            }
            viewModel.setExporting(false, if (ok) "PDF généré" else "Rien à exporter ou erreur lors du PDF")
        }
    }

    companion object {
        /** Onglet à ouvrir (index de [HistoryTab]). */
        const val EXTRA_TAB = "tab"
        const val TAB_SALES = 0
        const val TAB_QUOTES = 1
        const val TAB_OPERATIONS = 2
    }
}
