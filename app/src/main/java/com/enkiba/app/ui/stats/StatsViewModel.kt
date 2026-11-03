package com.enkiba.app.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.ExportRepository
import com.enkiba.app.data.repository.StatsRepository
import java.io.File
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
    val margin: MarginStats? = null,
    val stockValue: StockValue? = null,
    /** Export comptable en cours de création. */
    val exporting: Boolean = false,
    /** Fichier prêt à partager (consommé par l'écran). */
    val exportedFile: File? = null,
    val exportError: String? = null,
) {
    val lowStockCount: Int get() = stock.count { it.low }

    companion object {
        const val MONTHS_SHOWN = 5
    }
}

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StatsRepository(application)
    private val exportRepository = ExportRepository(application)
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    /** Recharge le mois affiché et le stock (retour sur l'écran, synchro). */
    fun reload() {
        viewModelScope.launch {
            val month = _state.value.selected
            val stats = repository.month(month)
            val margin = repository.margin(month)
            val stock = stockLines(repository.products())
            val value = repository.stockValue()
            _state.update { s ->
                if (s.selected == month) s.copy(loading = false, stats = stats, margin = margin, stock = stock, stockValue = value)
                else s.copy(stock = stock, stockValue = value)
            }
        }
    }

    fun selectMonth(month: MonthKey) {
        if (month == _state.value.selected) return
        _state.update { it.copy(selected = month, loading = true) }
        viewModelScope.launch {
            val stats = repository.month(month)
            val margin = repository.margin(month)
            _state.update { s -> if (s.selected == month) s.copy(loading = false, stats = stats, margin = margin) else s }
        }
    }

    /** Crée le classeur Excel du mois choisi. */
    fun export(month: MonthKey) {
        if (_state.value.exporting) return
        _state.update { it.copy(exporting = true, exportError = null) }
        viewModelScope.launch {
            val file = runCatching { exportRepository.export(month) }
                .onFailure { android.util.Log.e("StatsViewModel", "Export : ${it.message}", it) }
                .getOrNull()
            _state.update {
                it.copy(exporting = false, exportedFile = file, exportError = if (file == null) "L'export a échoué. Réessayez." else null)
            }
        }
    }

    fun exportHandled() = _state.update { it.copy(exportedFile = null, exportError = null) }
}
