package com.enkiba.app.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.HistoryRepository
import com.enkiba.app.models.ProductHistory
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.utils.SessionManager
import com.enkiba.app.utils.SynchronizationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loading: Boolean = true,
    val isAdmin: Boolean = false,
    val tab: HistoryTab = HistoryTab.SALES,
    val query: String = "",
    val quoteFilter: QuoteFilter = QuoteFilter.ALL,
    val sales: List<SaleHeader> = emptyList(),
    val quotes: List<Quote> = emptyList(),
    val operations: List<ProductHistory> = emptyList(),
    val refreshing: Boolean = false,
    val exporting: Boolean = false,
    val message: String? = null,
) {
    val tabs: List<HistoryTab> get() = if (isAdmin) HistoryTab.entries else listOf(HistoryTab.SALES, HistoryTab.QUOTES)
    val visibleSales: List<SaleHeader> get() = HistoryFilters.filterSales(sales, query)
    val visibleQuotes: List<Quote> get() = HistoryFilters.filterQuotes(quotes, quoteFilter, query)
    val visibleOperations: List<ProductHistory> get() = HistoryFilters.filterOperations(operations, query)
    fun quoteCount(f: QuoteFilter): Int = quotes.count(f::matches)
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = HistoryRepository(application)
    private val syncManager = SynchronizationManager(application)
    private val _state = MutableStateFlow(HistoryUiState(isAdmin = SessionManager(application).isAdmin))
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            val sales = repository.sales()
            val quotes = repository.quotes()
            val ops = if (_state.value.isAdmin) repository.operations() else emptyList()
            _state.update { it.copy(loading = false, sales = sales, quotes = quotes, operations = ops) }
        }
    }

    /** Onglet demandé à l'ouverture ; les opérations restent réservées aux admins. */
    fun selectTab(tab: HistoryTab) = _state.update { s -> s.copy(tab = if (tab in s.tabs) tab else HistoryTab.SALES, query = "") }

    fun onQueryChange(v: String) = _state.update { it.copy(query = v) }

    fun onQuoteFilter(f: QuoteFilter) = _state.update { it.copy(quoteFilter = f) }

    fun refresh() {
        _state.update { it.copy(refreshing = true) }
        syncManager.startSynchronization {
            _state.update { it.copy(refreshing = false) }
            reload()
        }
    }

    fun setExporting(on: Boolean, message: String? = null) = _state.update { it.copy(exporting = on, message = message ?: it.message) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    val repositoryForPdf: HistoryRepository get() = repository
}
