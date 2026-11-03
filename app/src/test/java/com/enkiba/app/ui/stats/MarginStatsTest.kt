package com.enkiba.app.ui.stats

import com.enkiba.app.data.local.entity.MarginRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarginStatsTest {

    @Test
    fun margeBruteSurLesLignesAvecPrixDAchat() {
        val m = marginStats(
            listOf(
                MarginRow("p1", "Câble", revenue = 3000.0, sold = 2, costPrice = 1000.0),   // marge 1000
                MarginRow("p1", "Câble", revenue = 1500.0, sold = 1, costPrice = 1200.0),   // marge 300
                MarginRow("p2", "Prise", revenue = 5000.0, sold = 1, costPrice = 4000.0),   // marge 1000
                MarginRow("p3", "Disjoncteur", revenue = 800.0, sold = 1, costPrice = null), // exclue
                MarginRow("p1", "Câble", revenue = 0.0, sold = 0, costPrice = 1000.0),      // tout retourné
            ),
        )
        assertEquals(2300.0, m.margin, 0.001)
        assertEquals(9500.0, m.coveredRevenue, 0.001)
        assertEquals(1, m.uncoveredLines)
        assertEquals(800.0, m.uncoveredRevenue, 0.001)
        assertEquals(listOf("p1", "p2"), m.top.map { it.productId })
        assertEquals(3, m.top.first().sold)
        assertEquals(2300.0 / 9500.0, m.rate!!, 0.0001)
    }

    @Test
    fun sansPrixDAchatPasDeTaux() {
        val m = marginStats(listOf(MarginRow("p1", "Câble", 1000.0, 1, null)))
        assertNull(m.rate)
        assertEquals(1, m.uncoveredLines)
    }
}
