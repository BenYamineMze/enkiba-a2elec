package com.enkiba.app.ui.quotes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.QuoteDetail
import com.enkiba.app.data.repository.QuoteDetailRepository
import com.enkiba.app.models.Quote
import com.enkiba.app.utils.SynchronizationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Étapes de la conversion : avertissements éventuels, puis choix du paiement. */
sealed interface ConversionStep {
    data class Warnings(val warnings: List<String>) : ConversionStep
    data object ChoosePayment : ConversionStep
    data object Converting : ConversionStep
}

data class QuoteDetailUiState(
    val loading: Boolean = true,
    val detail: QuoteDetail? = null,
    val notFound: Boolean = false,
    val conversion: ConversionStep? = null,
    val confirmDelete: Boolean = false,
    val pdfBusy: Boolean = false,
    val message: String? = null,
    /** Vente créée par la conversion : l'écran ouvre son détail. */
    val convertedSaleId: String? = null,
    val closed: Boolean = false,
) {
    val canConvert: Boolean get() = detail?.let { it.canEdit && it.quote.isConvertible } == true
    val canChangeStatus: Boolean get() = detail?.let { it.canEdit && it.quote.status != Quote.STATUS_CONVERTED } == true
}

class QuoteDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = QuoteDetailRepository(application)
    private val syncManager = SynchronizationManager(application)
    private val _state = MutableStateFlow(QuoteDetailUiState())
    val state: StateFlow<QuoteDetailUiState> = _state.asStateFlow()
    private var quoteId: String? = null

    fun load(id: String) {
        quoteId = id
        viewModelScope.launch {
            val d = repository.load(id)
            _state.update { it.copy(loading = false, detail = d, notFound = d == null) }
        }
    }

    fun setStatus(status: String, label: String) {
        val id = quoteId ?: return
        viewModelScope.launch {
            val ok = repository.setStatus(id, status)
            _state.update { it.copy(message = if (ok) "Devis marqué $label" else "Impossible de modifier ce devis") }
            if (ok) { load(id); syncManager.startSynchronization() }
        }
    }

    fun askDelete() = _state.update { it.copy(confirmDelete = true) }
    fun cancelDelete() = _state.update { it.copy(confirmDelete = false) }

    fun confirmDelete() {
        val id = quoteId ?: return
        _state.update { it.copy(confirmDelete = false) }
        viewModelScope.launch {
            if (repository.delete(id)) {
                syncManager.startSynchronization()
                _state.update { it.copy(closed = true, message = "Devis supprimé") }
            } else {
                _state.update { it.copy(message = "Impossible de supprimer ce devis") }
            }
        }
    }

    /** Démarre la conversion : avertissements d'abord s'il y en a. */
    fun startConversion() {
        val d = _state.value.detail ?: return
        viewModelScope.launch {
            val warnings = repository.conversionWarnings(d)
            _state.update { it.copy(conversion = if (warnings.isEmpty()) ConversionStep.ChoosePayment else ConversionStep.Warnings(warnings)) }
        }
    }

    fun continueAfterWarnings() = _state.update { it.copy(conversion = ConversionStep.ChoosePayment) }

    fun cancelConversion() = _state.update { s -> if (s.conversion == ConversionStep.Converting) s else s.copy(conversion = null) }

    fun convert(paymentMethod: String) {
        val d = _state.value.detail ?: return
        _state.update { it.copy(conversion = ConversionStep.Converting) }
        viewModelScope.launch {
            val saleId = repository.convert(d, paymentMethod)
            if (saleId == null) {
                _state.update { it.copy(conversion = null, message = "Conversion impossible (devis déjà converti ou erreur)") }
                quoteId?.let(::load)
            } else {
                syncManager.startSynchronization()
                _state.update { it.copy(conversion = null, convertedSaleId = saleId) }
            }
        }
    }

    fun setPdfBusy(on: Boolean, message: String? = null) = _state.update { it.copy(pdfBusy = on, message = message ?: it.message) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    val repositoryForPdf: QuoteDetailRepository get() = repository
}
