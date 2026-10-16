package com.enkiba.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDataTest {

    private fun data(today: Double, cash: Double, yesterday: Double) =
        HomeData(today, cash, today - cash, yesterday, 0, 0, 0, 0, 0.0, emptyList(), 0)

    @Test
    fun partDesEspeces() {
        assertEquals(0.75f, data(200.0, 150.0, 0.0).cashShare, 0.0001f)
        assertEquals(0f, data(0.0, 0.0, 0.0).cashShare, 0.0001f)
    }

    @Test
    fun evolutionParRapportAHier() {
        assertEquals(13, data(184_500.0, 0.0, 164_000.0).changeVsYesterdayPercent) // +12,5 % arrondi
        assertEquals(-50, data(50.0, 0.0, 100.0).changeVsYesterdayPercent)
        assertNull(data(100.0, 0.0, 0.0).changeVsYesterdayPercent) // pas de vente hier : pas de comparaison
    }
}
