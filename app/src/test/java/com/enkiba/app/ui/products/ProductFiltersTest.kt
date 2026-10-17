package com.enkiba.app.ui.products

import com.enkiba.app.models.Product
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductFiltersTest {

    private fun p(id: String, name: String, qty: Int, initial: Int = 0, threshold: Int = 5, category: String = "Divers", qr: String? = null) =
        Product().apply {
            this.id = id; this.name = name; quantity = qty; initial_stock = initial
            lowStockThreshold = threshold; this.category = category; qr_code = qr
        }

    private val products = listOf(
        p("1", "Câble 2,5 mm²", 120, initial = 150, category = "Câbles", qr = "PROD-AAA"),
        p("2", "Prise double", 3, initial = 20),
        p("3", "Interrupteur", 0, initial = 10),
        p("4", "ampoule LED", 5, threshold = 5, category = "Éclairage"),
    )

    @Test
    fun niveauxDeStock() {
        assertEquals(StockLevel.OK, ProductFilters.levelOf(products[0]))
        assertEquals(StockLevel.LOW, ProductFilters.levelOf(products[1]))
        assertEquals(StockLevel.OUT, ProductFilters.levelOf(products[2]))
        assertEquals(StockLevel.LOW, ProductFilters.levelOf(products[3])) // égal au seuil = faible
        assertEquals(StockLevel.OUT, ProductFilters.levelOf(p("5", "Négatif", -2))) // vente hors ligne concurrente
    }

    @Test
    fun anneauDeStock() {
        assertEquals(0.8f, ProductFilters.stockRatio(products[0]), 0.001f)
        assertEquals(0f, ProductFilters.stockRatio(products[2]), 0.001f)
        assertEquals(0.5f, ProductFilters.stockRatio(products[3]), 0.001f) // sans stock initial : 2 × le seuil
        assertEquals(1f, ProductFilters.stockRatio(p("6", "Réassort", 40, initial = 30)), 0.001f) // plafonné
    }

    @Test
    fun ongletsEtCompteurs() {
        assertEquals(4, ProductFilters.count(products, StockTab.ALL))
        assertEquals(2, ProductFilters.count(products, StockTab.LOW))
        assertEquals(1, ProductFilters.count(products, StockTab.OUT))
        assertEquals(listOf("4", "2"), ProductFilters.filter(products, StockTab.LOW, "").map { it.id })
    }

    @Test
    fun rechercheEtTri() {
        assertEquals(listOf("4", "1", "3", "2"), ProductFilters.filter(products, StockTab.ALL, "").map { it.id }) // alphabétique, sans casse
        assertEquals(listOf("1"), ProductFilters.filter(products, StockTab.ALL, "prod-a").map { it.id })
        assertEquals(listOf("4"), ProductFilters.filter(products, StockTab.ALL, "éclair").map { it.id })
        assertEquals(emptyList<String>(), ProductFilters.filter(products, StockTab.OUT, "câble").map { it.id })
    }
}
