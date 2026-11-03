package com.enkiba.app.ui.inventory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.local.entity.InventorySessionEntity
import com.enkiba.app.data.repository.InventoryRepository
import com.enkiba.app.data.repository.InventoryResult
import com.enkiba.app.models.Product
import com.enkiba.app.utils.SessionManager
import com.enkiba.app.utils.SynchronizationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class InventoryTab(val label: String) { TO_COUNT("À compter"), COUNTED("Comptés"), GAPS("Écarts") }

data class InventoryUiState(
    val loading: Boolean = true,
    val isAdmin: Boolean = false,
    val session: InventorySessionEntity? = null,
    val review: InventoryReview = InventoryReview(emptyList()),
    val query: String = "",
    val tab: InventoryTab = InventoryTab.TO_COUNT,
    /** Article en cours de saisie (fenêtre de comptage). */
    val editing: InventoryLine? = null,
    val busy: Boolean = false,
    val confirmValidate: Boolean = false,
    val confirmCancel: Boolean = false,
    val message: String? = null,
    /** Rapport PDF prêt à partager après la validation. */
    val report: File? = null,
) {
    /** Onglets visibles : les écarts sont réservés aux admins (comptage à l'aveugle). */
    val tabs: List<InventoryTab> get() = if (isAdmin) InventoryTab.entries else listOf(InventoryTab.TO_COUNT, InventoryTab.COUNTED)

    val visible: List<InventoryLine>
        get() = when (tab) {
            InventoryTab.TO_COUNT -> review.uncounted
            InventoryTab.COUNTED -> review.counted
            InventoryTab.GAPS -> review.gaps
        }.filter { InventoryRules.matches(it.product, query) }

    fun count(t: InventoryTab): Int = when (t) {
        InventoryTab.TO_COUNT -> review.uncounted.size
        InventoryTab.COUNTED -> review.counted.size
        InventoryTab.GAPS -> review.gaps.size
    }
}

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = InventoryRepository(application)
    private val syncManager = SynchronizationManager(application)
    private val _state = MutableStateFlow(InventoryUiState(isAdmin = SessionManager(application).isAdmin))
    val state: StateFlow<InventoryUiState> = _state.asStateFlow()

    fun reload() {
        viewModelScope.launch {
            val snap = repository.load()
            _state.update { it.copy(loading = false, session = snap.session, review = snap.review) }
        }
    }

    /** Synchronise (comptages des autres téléphones) puis recharge. */
    fun refresh() = syncManager.startSynchronization { reload() }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }
    fun onTab(t: InventoryTab) = _state.update { it.copy(tab = t) }

    fun start() {
        viewModelScope.launch {
            if (repository.start()) {
                reload()
                syncManager.startSynchronization { reload() }
            } else {
                _state.update { it.copy(message = "Un inventaire est déjà ouvert.") }
                refresh()
            }
        }
    }

    fun edit(line: InventoryLine) = _state.update { it.copy(editing = line) }
    fun closeEditor() = _state.update { it.copy(editing = null) }

    /** Code QR scanné : ouvre la saisie de l'article correspondant. */
    fun onScanned(code: String) {
        val line = _state.value.review.lines.firstOrNull { it.product.qr_code.equals(code.trim(), ignoreCase = true) }
        _state.update { if (line != null) it.copy(editing = line) else it.copy(message = "Aucun article ne porte ce code.") }
    }

    fun saveCount(product: Product, counted: Int) {
        val session = _state.value.session ?: return
        viewModelScope.launch {
            if (repository.saveCount(session.id, product.id, counted)) {
                _state.update { it.copy(editing = null, message = "${product.name} : $counted") }
                reload()
                syncManager.startSynchronization()
            } else {
                _state.update { it.copy(message = "Article introuvable") }
            }
        }
    }

    fun askValidate() = _state.update { it.copy(confirmValidate = true) }
    fun askCancel() = _state.update { it.copy(confirmCancel = true) }
    fun dismissDialogs() = _state.update { it.copy(confirmValidate = false, confirmCancel = false) }

    fun validate() {
        val session = _state.value.session ?: return
        _state.update { it.copy(confirmValidate = false, busy = true) }
        viewModelScope.launch {
            when (val r = repository.validate(session.id)) {
                is InventoryResult.Done -> _state.update {
                    it.copy(busy = false, report = r.report, message = "Inventaire validé : ${r.adjustments} ajustement${if (r.adjustments > 1) "s" else ""} de stock")
                }
                is InventoryResult.Failure -> _state.update { it.copy(busy = false, message = r.message) }
            }
            reload()
        }
    }

    fun cancel() {
        val session = _state.value.session ?: return
        _state.update { it.copy(confirmCancel = false, busy = true) }
        viewModelScope.launch {
            val r = repository.cancel(session.id)
            _state.update { it.copy(busy = false, message = if (r is InventoryResult.Failure) r.message else "Inventaire annulé, stock inchangé") }
            reload()
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
    fun reportHandled() = _state.update { it.copy(report = null) }
}
