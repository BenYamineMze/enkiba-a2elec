package com.enkiba.app.ui.sale

import com.enkiba.app.models.SaleItemRow

/**
 * Ligne de panier. Une même référence peut apparaître sur deux lignes si elle est
 * vendue à deux prix différents (prix catalogue et prix négocié).
 */
data class CartLine(
    val productId: String,
    val name: String,
    val listPrice: Double,
    val unitPrice: Double,
    val quantity: Int,
) {
    val total: Double get() = unitPrice * quantity
    val negotiated: Boolean get() = unitPrice != listPrice

    fun toRow(): SaleItemRow = SaleItemRow(productId, name, unitPrice, quantity)
}

/** Résultat d'un ajout au panier. */
sealed interface AddResult {
    data class Added(val lines: List<CartLine>) : AddResult
    data class StockExceeded(val available: Int) : AddResult
    data object InvalidQuantity : AddResult
    data object InvalidPrice : AddResult
}

/** Règles du panier (sans Android : testées en unitaire). */
object Cart {

    /** Remise au-delà de laquelle on demande une confirmation (20 %). */
    const val LARGE_DISCOUNT = 0.20

    fun quantityOf(lines: List<CartLine>, productId: String): Int =
        lines.filter { it.productId == productId }.sumOf { it.quantity }

    fun total(lines: List<CartLine>): Double = lines.sumOf { it.total }

    fun itemCount(lines: List<CartLine>): Int = lines.sumOf { it.quantity }

    /** Part de remise par rapport au prix catalogue (0 si pas de remise). */
    fun discountRate(listPrice: Double, unitPrice: Double): Double =
        if (listPrice <= 0.0 || unitPrice >= listPrice) 0.0 else (listPrice - unitPrice) / listPrice

    fun needsDiscountConfirmation(listPrice: Double, unitPrice: Double): Boolean =
        discountRate(listPrice, unitPrice) > LARGE_DISCOUNT

    /**
     * Ajoute une quantité au panier : fusion avec la ligne du même article au même prix,
     * jamais plus que le stock disponible (toutes lignes de cet article confondues).
     */
    fun add(
        lines: List<CartLine>,
        productId: String,
        name: String,
        listPrice: Double,
        unitPrice: Double,
        quantity: Int,
        stock: Int,
    ): AddResult {
        if (quantity <= 0) return AddResult.InvalidQuantity
        if (unitPrice < 0.0) return AddResult.InvalidPrice
        val alreadyInCart = quantityOf(lines, productId)
        if (alreadyInCart + quantity > stock) return AddResult.StockExceeded(maxOf(0, stock - alreadyInCart))

        val index = lines.indexOfFirst { it.productId == productId && it.unitPrice == unitPrice }
        val updated = if (index >= 0) {
            lines.toMutableList().also { it[index] = it[index].copy(quantity = it[index].quantity + quantity) }
        } else {
            lines + CartLine(productId, name, listPrice, unitPrice, quantity)
        }
        return AddResult.Added(updated)
    }

    /** Change la quantité d'une ligne ; 0 la retire. Plafonnée au stock de l'article. */
    fun setQuantity(lines: List<CartLine>, line: CartLine, quantity: Int, stock: Int): List<CartLine> {
        if (quantity <= 0) return lines - line
        val others = quantityOf(lines, line.productId) - line.quantity
        val capped = minOf(quantity, maxOf(0, stock - others))
        if (capped <= 0) return lines - line
        return lines.map { if (it == line) it.copy(quantity = capped) else it }
    }
}
