package com.enkiba.app.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.ProductsRepository
import com.enkiba.app.models.Product
import com.enkiba.app.utils.SessionManager
import com.enkiba.app.utils.SynchronizationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductsUiState(
    val loading: Boolean = true,
    val isAdmin: Boolean = false,
    val products: List<Product> = emptyList(),
    val stockValue: Double = 0.0,
    val query: String = "",
    val tab: StockTab = StockTab.ALL,
    val refreshing: Boolean = false,
    val pendingDelete: Product? = null,
    val message: String? = null,
    /** Filtre « sans prix d'achat » (ouvert depuis les statistiques) ; null = pas de filtre. */
    val missingCostIds: Set<String>? = null,
) {
    val visible: List<Product>
        get() = ProductFilters.filter(products, tab, query).let { list ->
            missingCostIds?.let { ids -> list.filter { it.id in ids } } ?: list
        }
    fun count(t: StockTab): Int = ProductFilters.count(products, t)
}

class ProductsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductsRepository(application)
    private val syncManager = SynchronizationManager(application)

    private val _state = MutableStateFlow(ProductsUiState(isAdmin = SessionManager(application).isAdmin))
    val state: StateFlow<ProductsUiState> = _state.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            val products = repository.load()
            val value = repository.stockValue()
            // Le filtre se met à jour : un produit complété disparaît de la liste au retour
            val missing = if (_state.value.missingCostIds != null) repository.missingCostIds() else null
            _state.update { it.copy(loading = false, products = products, stockValue = value, missingCostIds = missing) }
        }
    }

    /** Affiche seulement les produits sans prix d'achat (admins). */
    fun showMissingCost() {
        if (!_state.value.isAdmin) return
        viewModelScope.launch {
            val ids = repository.missingCostIds()
            _state.update { it.copy(missingCostIds = ids, tab = StockTab.ALL) }
        }
    }

    fun clearMissingCost() = _state.update { it.copy(missingCostIds = null) }

    fun onQueryChange(v: String) = _state.update { it.copy(query = v) }

    fun onTabChange(t: StockTab) = _state.update { it.copy(tab = t) }

    /** Tirer pour actualiser : synchronisation puis rechargement. */
    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        syncManager.startSynchronization {
            _state.update { it.copy(refreshing = false) }
            reload()
        }
    }

    fun askDelete(p: Product) = _state.update { it.copy(pendingDelete = p) }

    fun cancelDelete() = _state.update { it.copy(pendingDelete = null) }

    fun confirmDelete() {
        val p = _state.value.pendingDelete ?: return
        _state.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            val ok = repository.delete(p)
            _state.update { it.copy(message = if (ok) "${p.name} supprimé" else "Suppression impossible") }
            if (ok) {
                reload()
                syncManager.startSynchronization()
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
