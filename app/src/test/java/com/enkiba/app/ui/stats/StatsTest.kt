package com.enkiba.app.ui.stats

import com.enkiba.app.data.local.entity.SaleNetRow
import com.enkiba.app.models.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class StatsTest {

    // Comores : UTC+3, sans heure d'été
    private val zone = TimeZone.getTimeZone("Indian/Comoro")
    private val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply { timeZone = zone }.parse("2026-01-16T10:00")!!

    @Test
    fun cinqDerniersMoisAvecChangementAnnee() {
        val months = lastMonths(5, now, zone)
        assertEquals(listOf(MonthKey(2025, 8), MonthKey(2025, 9), MonthKey(2025, 10), MonthKey(2025, 11), MonthKey(2026, 0)), months)
        assertEquals("Sept.", months.first().shortLabel)
        assertEquals("janvier", months.last().longLabel)
    }

    @Test
    fun moisLocalConvertiEnUtc() {
        assertEquals("2025-12-31T21:00" to "2026-01-31T21:00", monthRangeUtc(MonthKey(2026, 0), zone))
    }

    @Test
    fun ventesRegroupeesParJourLocal() {
        val rows = listOf(
            SaleNetRow("2026-01-16T06:00:00", "espece", 10_000.0),  // 16/01 9 h locale
            SaleNetRow("2026-01-15T22:30:00", "virement", 5_000.0), // 16/01 1 h 30 locale
            SaleNetRow("2026-01-15T08:00:00", "espece", 40_000.0),  // hier
            SaleNetRow("2026-01-12T08:00:00", "espece", 2_000.0),   // lundi 12
        )
        val s = monthStats(MonthKey(2026, 0), rows, now, zone)
        assertEquals(57_000.0, s.net, 0.001)
        assertEquals(52_000.0, s.cash, 0.001)
        assertEquals(5_000.0, s.transfer, 0.001)
        assertEquals(listOf("Auj.", "Hier", "Lun. 12"), s.days.map { it.label })
        assertEquals(15_000.0, s.days[0].total, 0.001)
        assertEquals(2, s.days[0].count)
        assertEquals("Hier", s.bestDay?.label)
        assertEquals(14_250.0, s.averageBasket, 0.001)
    }

    @Test
    fun moisVideSansDivisionParZero() {
        val s = monthStats(MonthKey(2026, 0), emptyList(), now, zone)
        assertEquals(0f, s.cashShare)
        assertEquals(0.0, s.averageBasket, 0.0)
        assertEquals(null, s.bestDay)
        assertEquals(1.0, s.maxDay, 0.0)
    }

    private fun product(name: String, qty: Int, initial: Int, threshold: Int = 2) = Product().apply {
        setName(name); quantity = qty; setInitial_stock(initial); lowStockThreshold = threshold
    }

    @Test
    fun stockTrieDuPlusEntame() {
        val lines = stockLines(listOf(product("A", 8, 10), product("B", 1, 10), product("C", 5, 0)))
        assertEquals(listOf("B", "A", "C"), lines.map { it.product.name })
        assertEquals(10, lines[0].percent)
        assertTrue(lines[0].low)
        assertEquals(100, lines[2].percent) // stock de départ inconnu : la quantité sert de référence
        assertFalse(lines[2].low)
    }
}
