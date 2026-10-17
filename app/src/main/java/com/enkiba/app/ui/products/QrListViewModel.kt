package com.enkiba.app.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.ProductsRepository
import com.enkiba.app.models.Product
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QrListUiState(
    val loading: Boolean = true,
    val products: List<Product> = emptyList(),
    val query: String = "",
    /** Article dont le code est affiché en grand. */
    val zoomed: Product? = null,
    val exporting: Boolean = false,
    val message: String? = null,
) {
    val visible: List<Product> get() = qrFilter(products, query)
}

class QrListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductsRepository(application)
    private val _state = MutableStateFlow(QrListUiState())
    val state: StateFlow<QrListUiState> = _state.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            val products = repository.load().sortedBy { it.name.orEmpty().lowercase() }
            _state.update { it.copy(loading = false, products = products) }
        }
    }

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }
    fun zoom(product: Product?) = _state.update { it.copy(zoomed = product) }
    fun setExporting(on: Boolean, message: String? = null) = _state.update { it.copy(exporting = on, message = message ?: it.message) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}
