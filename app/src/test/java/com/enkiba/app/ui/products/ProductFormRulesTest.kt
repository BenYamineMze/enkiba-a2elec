package com.enkiba.app.ui.products

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductFormRulesTest {

    private val valid = ProductForm(name = " Câble 2,5 mm² ", category = "Câbles", quantityText = "150", priceText = "1 500", thresholdText = "")

    @Test
    fun formulaireValide() {
        val (errors, v) = ProductFormRules.validate(valid)
        assertTrue(errors.isValid)
        assertNotNull(v)
        assertEquals("Câble 2,5 mm²", v!!.name)
        assertEquals(150, v.quantity)
        assertEquals(1500.0, v.price, 0.001)
        assertEquals(ProductFormRules.DEFAULT_THRESHOLD, v.threshold) // seuil vide = 5
    }

    @Test
    fun champsObligatoiresEtInvalides() {
        val (errors, v) = ProductFormRules.validate(ProductForm(name = "", category = " ", quantityText = "", priceText = "abc", thresholdText = "x"))
        assertNull(v)
        assertNotNull(errors.name); assertNotNull(errors.category); assertNotNull(errors.quantity)
        assertNotNull(errors.price); assertNotNull(errors.threshold)
    }

    @Test
    fun prix() {
        assertEquals(1500.5, ProductFormRules.parsePrice("1500,50")!!, 0.001)
        assertEquals(12500.0, ProductFormRules.parsePrice("12 500")!!, 0.001)
        assertNull(ProductFormRules.parsePrice(""))
        assertEquals("1500", ProductFormRules.priceText(1500.0))
        assertEquals("1500.5", ProductFormRules.priceText(1500.5))
    }

    @Test
    fun jaugeNonRemiseAZeroQuandSeulLePrixChange() {
        assertEquals(150, ProductFormRules.newInitialStock(oldInitial = 150, oldQuantity = 120, newQuantity = 120))
        assertEquals(200, ProductFormRules.newInitialStock(oldInitial = 150, oldQuantity = 120, newQuantity = 200))
    }

    @Test
    fun codeQr() {
        val code = ProductFormRules.newQrCode()
        assertTrue(code.matches(Regex("PROD-[0-9A-F]{8}")))
    }

    @Test
    fun prixDAchatFacultatifMaisNonEffacable() {
        val base = ProductForm(name = "Câble", category = "Élec", quantityText = "3", priceText = "2000")
        assertEquals(null, ProductFormRules.validate(base).second!!.cost)
        assertEquals(1500.0, ProductFormRules.validate(base.copy(costText = "1 500")).second!!.cost!!, 0.001)
        assertEquals("Prix d'achat invalide", ProductFormRules.validate(base.copy(costText = "abc")).first.cost)
        // Déjà saisi : on peut le corriger, pas l'effacer
        assertNotNull(ProductFormRules.validate(base, hadCost = true).first.cost)
    }

    @Test
    fun margeUnitaire() {
        val m = UnitMargin.of(price = 2000.0, cost = 1500.0)
        assertEquals(500.0, m.amount, 0.001)
        assertEquals("25 %", percentText(m.rate!!))
        assertTrue(UnitMargin.of(1000.0, 1200.0).isLoss)
        assertNull(UnitMargin.of(0.0, 10.0).rate)
    }
}
