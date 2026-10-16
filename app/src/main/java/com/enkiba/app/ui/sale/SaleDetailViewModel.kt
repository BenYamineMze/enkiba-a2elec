package com.enkiba.app.ui.sale

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.SaleDetail
import com.enkiba.app.data.repository.SaleDetailRepository
import com.enkiba.app.models.SaleItemRow
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.utils.SynchronizationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Saisie d'un retour sur une ligne (quantité ≤ quantité encore retournable). */
data class ReturnEditor(val item: SaleItemRow, val quantity: Int = 1, val busy: Boolean = false) {
    val max: Int get() = item.quantity
    val refund: Double get() = quantity * item.unitPrice
}

data class SaleDetailUiState(
    val loading: Boolean = true,
    val detail: SaleDetail? = null,
    val notFound: Boolean = false,
    val returnEditor: ReturnEditor? = null,
    val pdfBusy: Boolean = false,
    val message: String? = null,
)

class SaleDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SaleDetailRepository(application)
    private val syncManager = SynchronizationManager(application)
    private val _state = MutableStateFlow(SaleDetailUiState())
    val state: StateFlow<SaleDetailUiState> = _state.asStateFlow()
    private var saleId: String? = null

    fun load(id: String) {
        saleId = id
        viewModelScope.launch {
            val d = repository.load(id)
            _state.update { it.copy(loading = false, detail = d, notFound = d == null) }
        }
    }

    fun openReturn(item: SaleItemRow) {
        if (item.quantity > 0) _state.update { it.copy(returnEditor = ReturnEditor(item)) }
    }

    fun setReturnQuantity(q: Int) = _state.update { s ->
        s.copy(returnEditor = s.returnEditor?.let { it.copy(quantity = q.coerceIn(1, it.max)) })
    }

    fun dismissReturn() = _state.update { s -> if (s.returnEditor?.busy == true) s else s.copy(returnEditor = null) }

    fun confirmReturn() {
        val editor = _state.value.returnEditor ?: return
        if (editor.busy) return
        _state.update { it.copy(returnEditor = editor.copy(busy = true)) }
        viewModelScope.launch {
            val refund = repository.returnItems(editor.item.id, editor.quantity)
            _state.update {
                it.copy(
                    returnEditor = null,
                    message = if (refund < 0) "Retour refusé (quantité invalide)" else "Retour enregistré : ${formatKmf(refund)} KMF remboursés",
                )
            }
            if (refund >= 0) {
                saleId?.let(::load)
                syncManager.startSynchronization()
            }
        }
    }

    fun setPdfBusy(on: Boolean, message: String? = null) = _state.update { it.copy(pdfBusy = on, message = message ?: it.message) }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    val repositoryForPdf: SaleDetailRepository get() = repository
}
