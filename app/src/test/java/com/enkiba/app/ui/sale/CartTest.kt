package com.enkiba.app.ui.sale

import com.enkiba.app.models.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CartTest {

    private fun added(r: AddResult): List<CartLine> = (r as AddResult.Added).lines

    @Test
    fun memeArticleMemePrixFusionne() {
        var cart = added(Cart.add(emptyList(), "p1", "Câble", 1500.0, 1500.0, 2, stock = 10))
        cart = added(Cart.add(cart, "p1", "Câble", 1500.0, 1500.0, 3, stock = 10))
        assertEquals(1, cart.size)
        assertEquals(5, cart[0].quantity)
        assertEquals(7500.0, Cart.total(cart), 0.001)
    }

    @Test
    fun prixNegocieSurUneLigneSeparee() {
        var cart = added(Cart.add(emptyList(), "p1", "Câble", 1500.0, 1500.0, 2, stock = 10))
        cart = added(Cart.add(cart, "p1", "Câble", 1500.0, 1200.0, 1, stock = 10))
        assertEquals(2, cart.size)
        assertTrue(cart[1].negotiated)
        assertEquals(3, Cart.quantityOf(cart, "p1"))
        assertEquals(4200.0, Cart.total(cart), 0.001)
    }

    @Test
    fun jamaisPlusQueLeStockToutesLignesConfondues() {
        val cart = added(Cart.add(emptyList(), "p1", "Prise", 3000.0, 3000.0, 2, stock = 3))
        val r = Cart.add(cart, "p1", "Prise", 3000.0, 2500.0, 2, stock = 3)
        assertEquals(AddResult.StockExceeded(available = 1), r)
        assertEquals(AddResult.StockExceeded(available = 0), Cart.add(emptyList(), "p2", "Rupture", 10.0, 10.0, 1, stock = 0))
    }

    @Test
    fun saisiesInvalides() {
        assertEquals(AddResult.InvalidQuantity, Cart.add(emptyList(), "p1", "X", 10.0, 10.0, 0, stock = 5))
        assertEquals(AddResult.InvalidPrice, Cart.add(emptyList(), "p1", "X", 10.0, -1.0, 1, stock = 5))
    }

    @Test
    fun remiseImportanteAuDelaDe20Pourcent() {
        assertFalse(Cart.needsDiscountConfirmation(1000.0, 800.0))   // 20 % pile : pas de confirmation
        assertTrue(Cart.needsDiscountConfirmation(1000.0, 790.0))    // 21 %
        assertFalse(Cart.needsDiscountConfirmation(1000.0, 1200.0))  // hausse : pas une remise
        assertEquals(0.25, Cart.discountRate(2000.0, 1500.0), 0.0001)
    }

    @Test
    fun modifierLaQuantiteDUneLigne() {
        val line = CartLine("p1", "Câble", 1500.0, 1500.0, 2)
        val other = CartLine("p1", "Câble", 1500.0, 1200.0, 1)
        val cart = listOf(line, other)
        assertEquals(listOf(other), Cart.setQuantity(cart, line, 0, stock = 10))         // 0 = retirée
        assertEquals(4, Cart.setQuantity(cart, line, 9, stock = 5)[0].quantity)           // plafonnée : 5 - 1
        assertEquals(3, Cart.setQuantity(cart, line, 3, stock = 10)[0].quantity)
    }
}

class SaleUiStateTest {

    private fun product(id: String, name: String, category: String, qr: String? = null) =
        Product().apply { this.id = id; this.name = name; this.category = category; qr_code = qr; price = 100.0; quantity = 5 }

    private val products = listOf(
        product("1", "Câble 2,5 mm²", "Câbles", "PROD-AAA"),
        product("2", "Ampoule LED 12 W", "Éclairage"),
        product("3", "Disjoncteur 16 A", "Protection"),
    )

    @Test
    fun filtreParCategorieEtRecherche() {
        val base = SaleUiState(loading = false, products = products)
        assertEquals(listOf("Câbles", "Protection", "Éclairage"), base.categories) // triées, sans doublon
        assertEquals(3, base.visibleProducts.size)
        assertEquals(listOf("2"), base.copy(category = "Éclairage").visibleProducts.map { it.id })
        assertEquals(listOf("3"), base.copy(query = "disj").visibleProducts.map { it.id })
        assertEquals(listOf("1"), base.copy(query = "prod-aaa").visibleProducts.map { it.id }) // code QR exact
        assertEquals(emptyList<String>(), base.copy(query = "led", category = "Câbles").visibleProducts.map { it.id })
    }

    @Test
    fun saisieDUnArticle() {
        val p = products[0]
        val e = ItemEditor(p, quantityText = "3")
        assertEquals(300.0, e.lineTotal, 0.001)
        assertEquals(240.0, e.copy(negotiated = true, priceText = "80").lineTotal, 0.001)
        assertEquals(null, e.copy(quantityText = "abc").quantity)
        assertEquals(85.5, e.copy(negotiated = true, priceText = "85,5").unitPrice!!, 0.001)
    }
}
