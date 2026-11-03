package com.enkiba.app.ui.inventory

import com.enkiba.app.data.local.entity.InventoryCountEntity
import com.enkiba.app.models.Product
import java.util.Locale

/** Un article dans l'inventaire : son dernier comptage (ou aucun). */
data class InventoryLine(
    val product: Product,
    val count: InventoryCountEntity?,
    /** Valeur d'une unité pour chiffrer les écarts : prix d'achat si connu, sinon prix de vente. */
    val unitValue: Double,
    val valueIsCost: Boolean,
) {
    /** Compté - attendu ; null si l'article n'a pas été compté. */
    val diff: Int? get() = count?.let { it.counted - it.expected }
    val gapValue: Double get() = (diff ?: 0) * unitValue
}

/** Récapitulatif avant validation et contenu du rapport. */
data class InventoryReview(val lines: List<InventoryLine>) {
    val counted: List<InventoryLine> get() = lines.filter { it.count != null }
    val uncounted: List<InventoryLine> get() = lines.filter { it.count == null }
    val gaps: List<InventoryLine> get() = counted.filter { it.diff != 0 }.sortedBy { it.gapValue }
    val missingUnits: Int get() = gaps.sumOf { (it.diff ?: 0).coerceAtMost(0) } * -1
    val surplusUnits: Int get() = gaps.sumOf { (it.diff ?: 0).coerceAtLeast(0) }
    val missingValue: Double get() = gaps.filter { (it.diff ?: 0) < 0 }.sumOf { -it.gapValue }
    val surplusValue: Double get() = gaps.filter { (it.diff ?: 0) > 0 }.sumOf { it.gapValue }
    /** Écart net en valeur (négatif = perte). */
    val netValue: Double get() = surplusValue - missingValue
}

object InventoryRules {

    /** [costs] : prix d'achat par produit (vide pour un vendeur). */
    fun review(products: List<Product>, counts: List<InventoryCountEntity>, costs: Map<String, Double>): InventoryReview {
        val byProduct = counts.associateBy { it.productId }
        return InventoryReview(
            products.map { p ->
                val cost = costs[p.id]
                InventoryLine(p, byProduct[p.id], cost ?: p.price, cost != null)
            }.sortedBy { it.product.name.orEmpty().lowercase(Locale.ROOT) },
        )
    }

    /** Quantité saisie : entier positif ou nul, au plus 6 chiffres. */
    fun parseCount(text: String): Int? = text.trim().takeIf { it.length in 1..6 && it.all(Char::isDigit) }?.toInt()

    /** Recherche par nom, catégorie ou code QR. */
    fun matches(p: Product, query: String): Boolean {
        val q = query.trim().lowercase(Locale.ROOT)
        return q.isEmpty() || p.name.orEmpty().lowercase(Locale.ROOT).contains(q) ||
            p.category.orEmpty().lowercase(Locale.ROOT).contains(q) || p.qr_code.orEmpty().lowercase(Locale.ROOT) == q
    }
}
