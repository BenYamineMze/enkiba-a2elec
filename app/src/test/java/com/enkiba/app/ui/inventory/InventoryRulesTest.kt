package com.enkiba.app.ui.inventory

import com.enkiba.app.data.local.entity.InventoryCountEntity
import com.enkiba.app.models.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRulesTest {

    private fun product(id: String, name: String, qty: Int, price: Double) =
        Product(name, "Élec", qty, price, null, 5, qty).apply { this.id = id; qr_code = "QR-$id" }

    private fun count(productId: String, counted: Int, expected: Int) =
        InventoryCountEntity("c-$productId", "s", "inv", productId, counted, expected, "u1", "2026-11-03T10:00:00Z", null, "synced")

    @Test
    fun ecartsChiffresAuPrixDAchatSinonAuPrixDeVente() {
        val products = listOf(product("p1", "Câble", 7, 1500.0), product("p2", "Prise", 4, 2500.0), product("p3", "Ampoule", 10, 500.0))
        val r = InventoryRules.review(
            products,
            listOf(count("p1", 5, 7), count("p2", 6, 4)),
            costs = mapOf("p1" to 1000.0),
        )
        assertEquals(2, r.counted.size)
        assertEquals(listOf("p3"), r.uncounted.map { it.product.id })
        assertEquals(2, r.missingUnits)
        assertEquals(2000.0, r.missingValue, 0.001)   // 2 câbles au prix d'achat
        assertEquals(2, r.surplusUnits)
        assertEquals(5000.0, r.surplusValue, 0.001)   // 2 prises au prix de vente
        assertEquals(3000.0, r.netValue, 0.001)
        assertEquals("p1", r.gaps.first().product.id) // la perte d'abord
        assertTrue(r.lines.single { it.product.id == "p1" }.valueIsCost)
    }

    @Test
    fun saisieDeLaQuantite() {
        assertEquals(12, InventoryRules.parseCount(" 12 "))
        assertEquals(0, InventoryRules.parseCount("0"))
        assertNull(InventoryRules.parseCount(""))
        assertNull(InventoryRules.parseCount("-3"))
        assertNull(InventoryRules.parseCount("1234567"))
    }

    @Test
    fun recherche() {
        val p = product("p1", "Câble rigide", 1, 1.0)
        assertTrue(InventoryRules.matches(p, "rigide"))
        assertTrue(InventoryRules.matches(p, "qr-p1"))
        assertFalse(InventoryRules.matches(p, "qr-p"))  // un code QR doit être exact
    }
}
