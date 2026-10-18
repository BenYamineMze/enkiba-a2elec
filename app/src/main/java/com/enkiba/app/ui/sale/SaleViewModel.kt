package com.enkiba.app.ui.sale

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.ClientInput
import com.enkiba.app.data.repository.SaleRepository
import com.enkiba.app.data.repository.SavedQuote
import com.enkiba.app.models.Product
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.utils.SynchronizationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Saisie d'un article à ajouter (quantité, prix négocié facultatif). */
data class ItemEditor(
    val product: Product,
    val quantityText: String = "1",
    val negotiated: Boolean = false,
    val priceText: String = "",
    val error: String? = null,
    /** Remise importante détectée : on attend une confirmation explicite. */
    val confirmDiscountPercent: Int? = null,
) {
    val quantity: Int? get() = quantityText.trim().toIntOrNull()
    val unitPrice: Double? get() = if (negotiated) priceText.trim().replace(',', '.').toDoubleOrNull() else product.price
    val lineTotal: Double get() = (quantity ?: 0) * (unitPrice ?: 0.0)
}

enum class CheckoutMode { SALE, QUOTE }

/** Fenêtre de validation (vente ou devis). */
data class Checkout(
    val mode: CheckoutMode,
    val paymentMethod: String = PAYMENT_CASH,
    val withInvoice: Boolean = false,
    val client: ClientInput = ClientInput(),
    val busy: Boolean = false,
    val error: String? = null,
) {
    /** Le nom du client est demandé pour un devis, et pour une vente avec facture. */
    val clientRequired: Boolean get() = mode == CheckoutMode.QUOTE || withInvoice

    companion object {
        const val PAYMENT_CASH = "espece"
        const val PAYMENT_TRANSFER = "virement"
    }
}

/** Résultat à traiter par l'écran (PDF à ouvrir, message). */
sealed interface SaleEvent {
    data class SaleRecorded(val sale: SaleHeader, val openInvoice: Boolean) : SaleEvent
    data class QuoteRecorded(val saved: SavedQuote) : SaleEvent
}

data class SaleUiState(
    val loading: Boolean = true,
    val products: List<Product> = emptyList(),
    val query: String = "",
    val category: String? = null, // null = toutes
    val cart: List<CartLine> = emptyList(),
    val editor: ItemEditor? = null,
    val checkout: Checkout? = null,
    val message: String? = null,
    val event: SaleEvent? = null,
    /** Facture ou devis qu'on propose d'envoyer par WhatsApp (bouton du message de confirmation). */
    val shareOffer: SaleEvent? = null,
) {
    val categories: List<String>
        get() = products.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }.distinct().sorted()

    /** Produits filtrés par catégorie et par recherche (nom, catégorie ou code QR). */
    val visibleProducts: List<Product>
        get() {
            val q = query.trim().lowercase(Locale.ROOT)
            return products.filter { p ->
                (category == null || p.category == category) &&
                    (q.isEmpty() || p.name.orEmpty().lowercase(Locale.ROOT).contains(q) ||
                        p.category.orEmpty().lowercase(Locale.ROOT).contains(q) ||
                        p.qr_code.orEmpty().lowercase(Locale.ROOT) == q)
            }
        }

    val cartTotal: Double get() = Cart.total(cart)
    val cartCount: Int get() = Cart.itemCount(cart)

    fun inCart(productId: String): Int = Cart.quantityOf(cart, productId)
}

class SaleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SaleRepository(application)
    private val syncManager = SynchronizationManager(application)

    private val _state = MutableStateFlow(SaleUiState())
    val state: StateFlow<SaleUiState> = _state.asStateFlow()

    init {
        reloadProducts()
    }

    fun reloadProducts() {
        viewModelScope.launch {
            val products = repository.loadProducts()
            _state.update { it.copy(loading = false, products = products) }
        }
    }

    // --- Recherche et filtres ---

    fun onQueryChange(value: String) = _state.update { it.copy(query = value) }

    fun onCategorySelect(category: String?) = _state.update { it.copy(category = category) }

    // --- Ajout d'un article ---

    /** Ouvre la saisie pour un produit (carte touchée, scan, ouverture depuis la fiche produit). */
    fun openEditor(product: Product) = _state.update { it.copy(editor = ItemEditor(product, priceText = formatPrice(product.price))) }

    fun openEditorFor(productId: String) {
        viewModelScope.launch { repository.findById(productId)?.let(::openEditor) }
    }

    fun onQrScanned(code: String) {
        viewModelScope.launch {
            val product = repository.findByQr(code.trim())
            if (product == null) _state.update { it.copy(message = "Produit inconnu pour ce code QR") }
            else openEditor(product)
        }
    }

    fun updateEditor(transform: (ItemEditor) -> ItemEditor) =
        _state.update { s -> s.copy(editor = s.editor?.let { transform(it).copy(error = null, confirmDiscountPercent = null) }) }

    fun dismissEditor() = _state.update { it.copy(editor = null) }

    /** Ajout rapide (bouton +) : une unité au prix catalogue. */
    fun quickAdd(product: Product) = addToCart(product, 1, product.price)

    /** Valide la saisie ; une remise de plus de 20 % demande une seconde validation. */
    fun confirmEditor(discountConfirmed: Boolean = false) {
        val editor = _state.value.editor ?: return
        val qty = editor.quantity
        val price = editor.unitPrice
        when {
            qty == null || qty <= 0 -> setEditorError("Quantité invalide")
            price == null || price < 0 -> setEditorError("Prix invalide")
            !discountConfirmed && Cart.needsDiscountConfirmation(editor.product.price, price) ->
                _state.update { s ->
                    s.copy(editor = editor.copy(confirmDiscountPercent =
                        Math.round(Cart.discountRate(editor.product.price, price) * 100).toInt()))
                }
            else -> addToCart(editor.product, qty, price)
        }
    }

    private fun setEditorError(message: String) = _state.update { s -> s.copy(editor = s.editor?.copy(error = message)) }

    private fun addToCart(product: Product, quantity: Int, unitPrice: Double) {
        val s = _state.value
        when (val r = Cart.add(s.cart, product.id, product.name.orEmpty(), product.price, unitPrice, quantity, product.quantity)) {
            is AddResult.Added -> _state.update { it.copy(cart = r.lines, editor = null, message = "${product.name} ajouté au panier") }
            is AddResult.StockExceeded -> {
                val msg = if (r.available == 0) "Stock épuisé pour ${product.name}" else "Stock insuffisant : ${r.available} disponible(s)"
                if (s.editor != null) setEditorError(msg) else _state.update { it.copy(message = msg) }
            }
            AddResult.InvalidQuantity -> setEditorError("Quantité invalide")
            AddResult.InvalidPrice -> setEditorError("Prix invalide")
        }
    }

    // --- Panier ---

    fun setLineQuantity(line: CartLine, quantity: Int) {
        val stock = _state.value.products.firstOrNull { it.id == line.productId }?.quantity ?: line.quantity
        _state.update { s ->
            val cart = Cart.setQuantity(s.cart, line, quantity, stock)
            s.copy(cart = cart, checkout = if (cart.isEmpty()) null else s.checkout)
        }
    }

    // --- Validation ---

    fun openCheckout(mode: CheckoutMode) {
        if (_state.value.cart.isEmpty()) return
        _state.update { it.copy(checkout = Checkout(mode)) }
    }

    fun updateCheckout(transform: (Checkout) -> Checkout) =
        _state.update { s -> s.copy(checkout = s.checkout?.let { transform(it).copy(error = null) }) }

    fun dismissCheckout() = _state.update { s -> if (s.checkout?.busy == true) s else s.copy(checkout = null) }

    fun submitCheckout() {
        val s = _state.value
        val checkout = s.checkout ?: return
        if (checkout.busy || s.cart.isEmpty()) return
        if (checkout.clientRequired && checkout.client.isEmpty) {
            _state.update { st -> st.copy(checkout = st.checkout?.copy(error = "Le nom du client est obligatoire")) }
            return
        }
        _state.update { st -> st.copy(checkout = checkout.copy(busy = true)) }
        viewModelScope.launch {
            when (checkout.mode) {
                CheckoutMode.SALE -> {
                    val client = if (checkout.withInvoice) checkout.client else checkout.client.takeUnless { it.isEmpty }
                    val sale = repository.recordSale(s.cart, checkout.paymentMethod, client)
                    if (sale == null) {
                        failCheckout("Erreur lors de l'enregistrement de la vente")
                    } else {
                        afterSuccess(
                            "Vente #${sale.shortRef} enregistrée",
                            SaleEvent.SaleRecorded(sale, openInvoice = checkout.withInvoice),
                        )
                    }
                }
                CheckoutMode.QUOTE -> {
                    val saved = repository.createQuote(s.cart, checkout.client)
                    if (saved == null) failCheckout("Erreur lors de l'enregistrement du devis")
                    else afterSuccess("Devis ${saved.quote.shortRef} enregistré (Historique → Devis)", SaleEvent.QuoteRecorded(saved))
                }
            }
        }
    }

    private fun failCheckout(message: String) =
        _state.update { st -> st.copy(checkout = st.checkout?.copy(busy = false, error = message)) }

    /** Panier vidé, stock rechargé, synchronisation lancée. */
    private fun afterSuccess(message: String, event: SaleEvent) {
        // Partage proposé pour une facture (vente avec client) ou un devis, pas pour une vente simple
        val offer = when (event) {
            is SaleEvent.SaleRecorded -> event.takeIf { it.openInvoice }
            is SaleEvent.QuoteRecorded -> event
        }
        _state.update { it.copy(cart = emptyList(), checkout = null, message = message, event = event, shareOffer = offer) }
        reloadProducts()
        syncManager.startSynchronization()
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun consumeEvent() = _state.update { it.copy(event = null) }

    fun consumeShareOffer() = _state.update { it.copy(shareOffer = null) }

    fun showMessage(message: String) = _state.update { it.copy(message = message) }

    /** Client d'une vente (pour la facture). */
    suspend fun clientOf(sale: SaleHeader) = repository.clientOf(sale)

    val repositoryForPdf: SaleRepository get() = repository

    private fun formatPrice(price: Double): String =
        if (price % 1.0 == 0.0) price.toLong().toString() else price.toString()
}
