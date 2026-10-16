package com.enkiba.app.ui.sale

import com.enkiba.app.models.SaleItemRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaleDetailStateTest {

    @Test
    fun retourRembourseAuPrixApplique() {
        val row = SaleItemRow("p1", "Câble", 1200.0, 3).apply { listPrice = 1500.0 }
        val editor = ReturnEditor(row, quantity = 2)
        assertEquals(3, editor.max)
        assertEquals(2400.0, editor.refund, 0.001)
    }

    @Test
    fun prixCatalogueInconnuAfficheLePrixApplique() {
        val row = SaleItemRow("p1", "Câble", 1200.0, 1)
        assertEquals(1200.0, row.listPrice, 0.001)
        assertFalse(row.isNegotiated)
        row.listPrice = 1500.0
        assertTrue(row.isNegotiated)
    }
}
