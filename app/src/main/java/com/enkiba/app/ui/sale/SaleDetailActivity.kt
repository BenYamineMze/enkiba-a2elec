package com.enkiba.app.ui.sale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.launch

/** Détail d'une vente ([EXTRA_SALE_ID]) : retours et facture. */
class SaleDetailActivity : ComponentActivity() {

    private val viewModel: SaleDetailViewModel by viewModels()
    private var saleId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        saleId = intent.getStringExtra(EXTRA_SALE_ID)
        if (saleId == null) {
            finish(); return
        }
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SaleDetailScreen(
                    state = state,
                    onBack = ::finish,
                    onInvoice = ::openInvoice,
                    onReturn = viewModel::openReturn,
                    onReturnQuantity = viewModel::setReturnQuantity,
                    onConfirmReturn = viewModel::confirmReturn,
                    onDismissReturn = viewModel::dismissReturn,
                    onMessageShown = viewModel::consumeMessage,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        saleId?.let(viewModel::load)
    }

    private fun openInvoice() {
        val detail = viewModel.state.value.detail ?: return
        lifecycleScope.launch {
            viewModel.setPdfBusy(true)
            val ok = viewModel.repositoryForPdf.openInvoice(this@SaleDetailActivity, detail)
            viewModel.setPdfBusy(false, if (ok) null else "Erreur lors de la génération de la facture")
        }
    }

    companion object {
        const val EXTRA_SALE_ID = "sale_id"
    }
}
