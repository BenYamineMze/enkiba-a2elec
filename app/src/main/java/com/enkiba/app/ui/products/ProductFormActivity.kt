package com.enkiba.app.ui.products

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
import com.enkiba.app.utils.SessionManager

/**
 * Ajout (sans extra) ou modification ([EXTRA_PRODUCT_ID]) d'un article. Admin seulement :
 * le serveur refuse la création et la modification de produits aux vendeurs.
 */
class ProductFormActivity : ComponentActivity() {

    private val viewModel: ProductFormViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        if (!session.isAdmin) {
            Toast.makeText(this, "Réservé à l'administrateur", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        enableEdgeToEdge()
        viewModel.start(intent.getStringExtra(EXTRA_PRODUCT_ID))
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.done) {
                    if (state.done) {
                        Toast.makeText(this@ProductFormActivity, "Article modifié", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                LaunchedEffect(state.failure, state.loading) {
                    if (!state.loading && state.isEdit.not() && state.failure == "Produit introuvable") finish()
                }
                ProductFormScreen(state, ::finish, viewModel::update, viewModel::save, viewModel::consumeMessage)
            }
        }
    }

    companion object {
        /** Id (UUID) de l'article à modifier ; absent = ajout. */
        const val EXTRA_PRODUCT_ID = "PRODUCT_ID"
    }
}
