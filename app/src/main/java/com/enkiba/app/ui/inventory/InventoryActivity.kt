package com.enkiba.app.ui.inventory

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.FileShare
import com.enkiba.app.utils.SessionManager
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Inventaire : comptage par scan (tous les membres), validation des écarts (admin). */
class InventoryActivity : ComponentActivity() {

    private val viewModel: InventoryViewModel by viewModels()

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::onScanned)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        enableEdgeToEdge()
        if (savedInstanceState == null) viewModel.refresh()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.report) {
                    state.report?.let { pdf ->
                        if (!FileShare.share(this@InventoryActivity, pdf, "application/pdf", "Rapport d'inventaire", pdf.nameWithoutExtension)) {
                            Toast.makeText(this@InventoryActivity, "Rapport enregistré dans Documents/Enkiba/Rapports", Toast.LENGTH_LONG).show()
                        }
                        viewModel.reportHandled()
                    }
                }
                InventoryScreen(
                    state = state,
                    actions = InventoryActions(
                        onBack = ::finish,
                        onScan = ::scan,
                        onQuery = viewModel::onQuery,
                        onTab = viewModel::onTab,
                        onStart = viewModel::start,
                        onEdit = viewModel::edit,
                        onCloseEditor = viewModel::closeEditor,
                        onSave = viewModel::saveCount,
                        onAskValidate = viewModel::askValidate,
                        onAskCancel = viewModel::askCancel,
                        onDismiss = viewModel::dismissDialogs,
                        onValidate = viewModel::validate,
                        onCancel = viewModel::cancel,
                        onMessageShown = viewModel::messageShown,
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.reload()
    }

    private fun scan() = scanLauncher.launch(
        ScanOptions()
            .setPrompt("Scannez le code de l'article à compter")
            .setBeepEnabled(true)
            .setOrientationLocked(true)
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE),
    )
}
