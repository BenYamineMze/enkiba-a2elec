package com.enkiba.app.ui.quotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.enkiba.app.models.Quote
import com.enkiba.app.ui.sale.SaleDetailActivity
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.launch

/** Détail d'un devis ([EXTRA_QUOTE_ID]) : PDF, statut, suppression, conversion en vente. */
class QuoteDetailActivity : ComponentActivity() {

    private val viewModel: QuoteDetailViewModel by viewModels()
    private var quoteId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        quoteId = intent.getStringExtra(EXTRA_QUOTE_ID)
        if (quoteId == null) {
            finish(); return
        }
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.convertedSaleId) {
                    state.convertedSaleId?.let { openSale(it); finish() }
                }
                LaunchedEffect(state.closed) { if (state.closed) finish() }
                QuoteDetailScreen(
                    state = state,
                    actions = QuoteDetailActions(
                        onBack = ::finish,
                        onPdf = ::openPdf,
                        onAccept = { viewModel.setStatus(Quote.STATUS_ACCEPTED, "accepté") },
                        onReject = { viewModel.setStatus(Quote.STATUS_REJECTED, "refusé") },
                        onAskDelete = viewModel::askDelete,
                        onConfirmDelete = viewModel::confirmDelete,
                        onCancelDelete = viewModel::cancelDelete,
                        onStartConversion = viewModel::startConversion,
                        onContinueConversion = viewModel::continueAfterWarnings,
                        onConvert = viewModel::convert,
                        onCancelConversion = viewModel::cancelConversion,
                        onOpenSale = ::openSale,
                        onMessageShown = viewModel::consumeMessage,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        quoteId?.let(viewModel::load)
    }

    private fun openSale(saleId: String) =
        startActivity(Intent(this, SaleDetailActivity::class.java).putExtra(SaleDetailActivity.EXTRA_SALE_ID, saleId))

    private fun openPdf() {
        val detail = viewModel.state.value.detail ?: return
        lifecycleScope.launch {
            viewModel.setPdfBusy(true)
            val ok = viewModel.repositoryForPdf.openPdf(this@QuoteDetailActivity, detail)
            viewModel.setPdfBusy(false, if (ok) null else "Erreur lors de la génération du PDF")
        }
    }

    companion object {
        const val EXTRA_QUOTE_ID = "quote_id"
    }
}
