package com.enkiba.app.ui.products

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.ProductFormRepository
import com.enkiba.app.models.Product
import com.enkiba.app.utils.SynchronizationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductFormUiState(
    val loading: Boolean = true,
    /** null = ajout ; sinon l'article modifié. */
    val original: Product? = null,
    val form: ProductForm = ProductForm(),
    val errors: ProductFormErrors = ProductFormErrors(),
    val categories: List<String> = ProductFormRules.DEFAULT_CATEGORIES,
    val saving: Boolean = false,
    val message: String? = null,
    val failure: String? = null,
    /** Modification enregistrée : l'écran se ferme. */
    val done: Boolean = false,
) {
    val isEdit: Boolean get() = original != null

    /** Écart de stock saisi en modification (mouvement de stock qui sera créé). */
    val stockDelta: Int?
        get() = original?.let { o -> form.quantityText.trim().toIntOrNull()?.minus(o.quantity) }?.takeIf { it != 0 }
}

class ProductFormViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProductFormRepository(application)
    private val syncManager = SynchronizationManager(application)

    private val _state = MutableStateFlow(ProductFormUiState())
    val state: StateFlow<ProductFormUiState> = _state.asStateFlow()

    /** Ouvre le formulaire : ajout (productId null) ou modification. */
    fun start(productId: String?) {
        if (!_state.value.loading) return
        viewModelScope.launch {
            val categories = repository.categories()
            val product = productId?.let { repository.load(it) }
            _state.update {
                it.copy(
                    loading = false,
                    original = product,
                    categories = categories,
                    failure = if (productId != null && product == null) "Produit introuvable" else null,
                    form = product?.let { p ->
                        ProductForm(
                            name = p.name.orEmpty(),
                            category = p.category.orEmpty(),
                            quantityText = p.quantity.toString(),
                            priceText = ProductFormRules.priceText(p.price),
                            thresholdText = p.lowStockThreshold.toString(),
                            description = p.description.orEmpty(),
                        )
                    } ?: ProductForm(category = categories.firstOrNull().orEmpty()),
                )
            }
        }
    }

    fun update(transform: (ProductForm) -> ProductForm) =
        _state.update { it.copy(form = transform(it.form), errors = ProductFormErrors(), failure = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val (errors, valid) = ProductFormRules.validate(s.form)
        if (valid == null) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(saving = true, failure = null) }
        viewModelScope.launch {
            val original = s.original
            if (original == null) {
                val created = repository.create(valid)
                if (created == null) {
                    _state.update { it.copy(saving = false, failure = "Erreur lors de l'ajout") }
                } else {
                    // Le formulaire reste ouvert pour enchaîner les ajouts
                    _state.update {
                        it.copy(
                            saving = false,
                            form = ProductForm(category = s.form.category),
                            message = "${created.name} ajouté (code ${created.qr_code})",
                            categories = (it.categories + valid.category).distinct(),
                        )
                    }
                    syncManager.startSynchronization()
                }
            } else {
                if (repository.update(original, valid)) {
                    _state.update { it.copy(saving = false, done = true) }
                    syncManager.startSynchronization()
                } else {
                    _state.update { it.copy(saving = false, failure = "Erreur lors de la modification") }
                }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
