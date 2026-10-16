package com.enkiba.app.ui.history

import com.enkiba.app.models.ProductHistory
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.ui.components.parseIsoDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class HistoryFiltersTest {

    private val comoros = TimeZone.getTimeZone("Indian/Comoro")
    private val now = parseIsoDate("2026-10-16T12:00Z")!!

    private fun sale(id: String, seller: String, payment: String, date: String) =
        SaleHeader().apply { this.id = id; username = seller; paymentMethod = payment; saleDate = date }

    private fun quote(id: String, status: String, client: String, validUntil: String) =
        Quote().apply { this.id = id; this.status = status; clientName = client; this.validUntil = validUntil }

    @Test
    fun rechercheDesVentes() {
        val sales = listOf(
            sale("3f2a9c1b-0000", "Mze Ben", "espece", "2026-10-16T10:00Z"),
            sale("9a1b2c3d-0000", "Vendeur2 Test", "virement", "2026-10-15T10:00Z"),
        )
        assertEquals(listOf("3f2a9c1b-0000"), HistoryFilters.filterSales(sales, "#3F2A").map { it.id })
        assertEquals(listOf("9a1b2c3d-0000"), HistoryFilters.filterSales(sales, "vendeur2").map { it.id })
        assertEquals(listOf("9a1b2c3d-0000"), HistoryFilters.filterSales(sales, "vir").map { it.id })
        assertEquals(2, HistoryFilters.filterSales(sales, " ").size)
    }

    @Test
    fun filtreDesDevisParStatut() {
        val quotes = listOf(
            quote("a1", Quote.STATUS_DRAFT, "Ali Said", "2099-01-01"),
            quote("b2", Quote.STATUS_DRAFT, "Fatima", "2000-01-01"),     // brouillon expiré
            quote("c3", Quote.STATUS_ACCEPTED, "Moroni BTP", "2099-01-01"),
            quote("d4", Quote.STATUS_CONVERTED, "Ali Said", "2000-01-01"), // converti : jamais expiré
            quote("e5", Quote.STATUS_REJECTED, "X", "2099-01-01"),
        )
        fun ids(f: QuoteFilter, q: String = "") = HistoryFilters.filterQuotes(quotes, f, q).map { it.id }
        assertEquals(listOf("a1"), ids(QuoteFilter.DRAFT))
        assertEquals(listOf("b2"), ids(QuoteFilter.EXPIRED))
        assertEquals(listOf("c3"), ids(QuoteFilter.ACCEPTED))
        assertEquals(listOf("d4"), ids(QuoteFilter.CONVERTED))
        assertEquals(listOf("e5"), ids(QuoteFilter.REJECTED))
        assertEquals(listOf("a1", "d4"), ids(QuoteFilter.ALL, "ali"))
        assertEquals(listOf("c3"), ids(QuoteFilter.ALL, "DEV-c3"))
    }

    @Test
    fun rechercheDesOperations() {
        val ops = listOf(
            ProductHistory().apply { id = "1"; productName = "Câble"; operation = "Vente (Négocié: 1500 -> 1200)"; username = "Mze" },
            ProductHistory().apply { id = "2"; productName = "Prise"; operation = "RETOUR CLIENT"; username = "Toto" },
        )
        assertEquals(listOf("1"), HistoryFilters.filterOperations(ops, "négocié").map { it.id })
        assertEquals(listOf("2"), HistoryFilters.filterOperations(ops, "toto").map { it.id })
    }

    @Test
    fun libellesDesJours() {
        assertEquals("Aujourd'hui", HistoryFilters.dayLabel("2026-10-16T05:00Z", now, comoros))
        assertEquals("Hier", HistoryFilters.dayLabel("2026-10-15T20:00Z", now, comoros))   // 23:00 aux Comores, le 15
        assertEquals("Aujourd'hui", HistoryFilters.dayLabel("2026-10-15T21:30Z", now, comoros)) // 00:30 le 16
        assertEquals("Mercredi 14 octobre", HistoryFilters.dayLabel("2026-10-14T09:00:00+00:00", now, comoros))
        assertEquals("Date inconnue", HistoryFilters.dayLabel(null, now, comoros))
    }

    @Test
    fun regroupementParJour() {
        val sales = listOf(
            sale("1", "", "espece", "2026-10-16T10:00Z"),
            sale("2", "", "espece", "2026-10-16T08:00Z"),
            sale("3", "", "espece", "2026-10-15T10:00Z"),
        )
        val groups = HistoryFilters.groupByDay(sales, { it.saleDate }, now, comoros)
        assertEquals(listOf("Aujourd'hui", "Hier"), groups.map { it.label })
        assertEquals(listOf("1", "2"), groups[0].items.map { it.id })
    }
}
