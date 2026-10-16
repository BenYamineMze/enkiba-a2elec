package com.enkiba.app.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
    val months: List<MonthKey> = lastMonths(MONTHS_SHOWN),
    val selected: MonthKey = months.last(),
    val loading: Boolean = true,
    val stats: MonthStats? = null,
    val stock: List<StockLine> = emptyList(),
) {
    val lowStockCount: Int get() = stock.count { it.low }

    companion object {
        const val MONTHS_SHOWN = 5
    }
}

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StatsRepository(application)
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    /** Recharge le mois affiché et le stock (retour sur l'écran, synchro). */
    fun reload() {
        viewModelScope.launch {
            val month = _state.value.selected
            val stats = repository.month(month)
            val stock = stockLines(repository.products())
            _state.update { s -> if (s.selected == month) s.copy(loading = false, stats = stats, stock = stock) else s.copy(stock = stock) }
        }
    }

    fun selectMonth(month: MonthKey) {
        if (month == _state.value.selected) return
        _state.update { it.copy(selected = month, loading = true) }
        viewModelScope.launch {
            val stats = repository.month(month)
            _state.update { s -> if (s.selected == month) s.copy(loading = false, stats = stats) else s }
        }
    }
}
