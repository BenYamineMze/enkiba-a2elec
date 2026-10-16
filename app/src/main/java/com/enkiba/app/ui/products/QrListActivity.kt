package com.enkiba.app.ui.products

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.PDFUtils
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Codes QR des articles (Compose) : recherche, agrandissement, planche PDF à imprimer. */
class QrListActivity : ComponentActivity() {

    private val viewModel: QrListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                QrListScreen(
                    state = state,
                    actions = QrListActions(
                        onBack = ::finish,
                        onQueryChange = viewModel::onQueryChange,
                        onZoom = viewModel::zoom,
                        onExport = ::export,
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

    /** PDF des articles affichés, ouvert avec le contexte de l'activité. */
    private fun export() {
        val list = viewModel.state.value.visible
        if (list.isEmpty()) {
            viewModel.setExporting(false, "Aucun code à exporter"); return
        }
        lifecycleScope.launch {
            viewModel.setExporting(true)
            val ok = withContext(Dispatchers.IO) { PDFUtils.generateQrListPdf(this@QrListActivity, list) }
            viewModel.setExporting(false, if (ok) null else "Erreur lors de la génération du PDF")
        }
    }
}
