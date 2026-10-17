package com.enkiba.app.ui.products

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.ProductFormRepository
import com.enkiba.app.models.Product
import com.enkiba.app.utils.QrUtils
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProductDetailUiState(
    val loading: Boolean = true,
    val product: Product? = null,
    val qr: Bitmap? = null,
    val isAdmin: Boolean = false,
    val notFound: Boolean = false,
)

class ProductDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductFormRepository(application)

    private val _state = MutableStateFlow(ProductDetailUiState(isAdmin = SessionManager(application).isAdmin))
    val state: StateFlow<ProductDetailUiState> = _state.asStateFlow()

    /** (Re)charge l'article : rappelé au retour d'une modification ou d'une vente. */
    fun load(productId: String) {
        viewModelScope.launch {
            val p = repository.load(productId)
            val qr = p?.qr_code?.takeIf { it.isNotBlank() }?.let { code ->
                withContext(Dispatchers.Default) {
                    runCatching { QrUtils.generateQrBitmap(code) }.onFailure { Log.e("ProductDetail", "QR : ${it.message}") }.getOrNull()
                }
            }
            _state.update { it.copy(loading = false, product = p, qr = qr ?: it.qr, notFound = p == null || p.deletedAt != null) }
        }
    }
}
