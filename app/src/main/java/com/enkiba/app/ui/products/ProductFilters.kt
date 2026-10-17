package com.enkiba.app.ui.products

import com.enkiba.app.models.Product
import java.util.Locale

/** Onglets de la liste des produits. */
enum class StockTab(val label: String) { ALL("Tous"), LOW("Stock faible"), OUT("Rupture") }

/** État du stock d'un article. */
enum class StockLevel { OK, LOW, OUT }

/** Règles de la liste des produits (sans Android : testées en unitaire). */
object ProductFilters {

    fun levelOf(p: Product): StockLevel = when {
        p.quantity <= 0 -> StockLevel.OUT
        p.quantity <= p.lowStockThreshold -> StockLevel.LOW
        else -> StockLevel.OK
    }

    /** Part du stock restant par rapport au stock de référence (0 à 1). */
    fun stockRatio(p: Product): Float {
        if (p.quantity <= 0) return 0f
        val reference = if (p.initial_stock > 0) p.initial_stock else maxOf(p.quantity, p.lowStockThreshold * 2, 1)
        return (p.quantity.toFloat() / reference).coerceIn(0f, 1f)
    }

    fun count(products: List<Product>, tab: StockTab): Int = products.count { matchesTab(it, tab) }

    fun matchesTab(p: Product, tab: StockTab): Boolean = when (tab) {
        StockTab.ALL -> true
        StockTab.LOW -> levelOf(p) == StockLevel.LOW
        StockTab.OUT -> levelOf(p) == StockLevel.OUT
    }

    /** Recherche dans le nom, la catégorie et le code QR ; tri alphabétique. */
    fun filter(products: List<Product>, tab: StockTab, query: String): List<Product> {
        val q = query.trim().lowercase(Locale.ROOT)
        return products
            .filter { matchesTab(it, tab) }
            .filter {
                q.isEmpty() || it.name.orEmpty().lowercase(Locale.ROOT).contains(q) ||
                    it.category.orEmpty().lowercase(Locale.ROOT).contains(q) ||
                    it.qr_code.orEmpty().lowercase(Locale.ROOT).contains(q)
            }
            .sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }
    }
}
