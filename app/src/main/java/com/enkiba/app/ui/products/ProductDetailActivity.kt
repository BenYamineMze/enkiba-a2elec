package com.enkiba.app.ui.products

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enkiba.app.ui.sale.SaleActivity
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.utils.SessionManager

/** Fiche d'un article ([EXTRA_PRODUCT_ID]). */
class ProductDetailActivity : ComponentActivity() {

    private val viewModel: ProductDetailViewModel by viewModels()
    private var productId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = SessionManager(this)
        if (!session.isLoggedIn) {
            session.logout(); finish(); return
        }
        productId = intent.getStringExtra(EXTRA_PRODUCT_ID)
        if (productId == null) {
            finish(); return
        }
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                ProductDetailScreen(
                    state = state,
                    onBack = ::finish,
                    onSell = { p -> startActivity(Intent(this, SaleActivity::class.java).putExtra(SaleActivity.EXTRA_PRODUCT_ID, p.id)) },
                    onEdit = { p -> startActivity(Intent(this, ProductFormActivity::class.java).putExtra(ProductFormActivity.EXTRA_PRODUCT_ID, p.id)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Au retour d'une modification ou d'une vente
        productId?.let(viewModel::load)
    }

    companion object {
        const val EXTRA_PRODUCT_ID = "id"
    }
}
