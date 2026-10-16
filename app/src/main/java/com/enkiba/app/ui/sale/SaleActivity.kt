package com.enkiba.app.ui.sale

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
import com.enkiba.app.ui.history.HistoryActivity
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/**
 * Caisse (Compose) : recherche ou scan d'un article, panier, encaissement ou devis.
 * Extra facultatif [EXTRA_PRODUCT_ID] : ouvre directement la saisie de cet article.
 */
class SaleActivity : ComponentActivity() {

    private val viewModel: SaleViewModel by viewModels()

    private val qrLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::onQrScanned)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout()
            finish()
            return
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) intent.getStringExtra(EXTRA_PRODUCT_ID)?.let(viewModel::openEditorFor)

        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                // PDF à ouvrir après une vente avec facture ou un devis (contexte d'activité requis)
                LaunchedEffect(state.event) {
                    when (val event = state.event) {
                        is SaleEvent.SaleRecorded -> if (event.openInvoice) {
                            val client = viewModel.clientOf(event.sale)
                            viewModel.repositoryForPdf.openInvoicePdf(this@SaleActivity, event.sale, client)
                        }
                        is SaleEvent.QuoteRecorded -> viewModel.repositoryForPdf.openQuotePdf(this@SaleActivity, event.saved)
                        null -> Unit
                    }
                    if (state.event != null) viewModel.consumeEvent()
                }

                SaleScreen(
                    state = state,
                    actions = SaleActions(
                        onBack = ::finish,
                        onHistory = {
                            startActivity(Intent(this, HistoryActivity::class.java).putExtra(HistoryActivity.EXTRA_TAB, HistoryActivity.TAB_SALES))
                        },
                        onScan = ::startScan,
                        onQueryChange = viewModel::onQueryChange,
                        onCategorySelect = viewModel::onCategorySelect,
                        onProductClick = viewModel::openEditor,
                        onQuickAdd = viewModel::quickAdd,
                        onEditorChange = viewModel::updateEditor,
                        onEditorConfirm = viewModel::confirmEditor,
                        onEditorDismiss = viewModel::dismissEditor,
                        onOpenCheckout = viewModel::openCheckout,
                        onLineQuantity = viewModel::setLineQuantity,
                        onCheckoutChange = viewModel::updateCheckout,
                        onCheckoutSubmit = viewModel::submitCheckout,
                        onCheckoutDismiss = viewModel::dismissCheckout,
                        onMessageShown = viewModel::consumeMessage,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Le stock a pu changer (synchro, retour, autre écran)
        lifecycleScope.launch { viewModel.reloadProducts() }
    }

    private fun startScan() {
        qrLauncher.launch(
            ScanOptions()
                .setPrompt("Scannez le code de l'article")
                .setBeepEnabled(true)
                .setOrientationLocked(true)
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        )
    }

    companion object {
        /** Id (UUID) d'un article à ajouter dès l'ouverture (depuis la fiche produit). */
        const val EXTRA_PRODUCT_ID = "id"
    }
}
