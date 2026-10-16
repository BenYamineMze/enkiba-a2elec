package com.enkiba.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Formatage des montants en francs comoriens (pas de centimes). */
class MoneyTest {

    private val nb = ' ' // espace fine insécable

    @Test
    fun separateurDesMilliers() {
        assertEquals("0", formatKmf(0.0))
        assertEquals("950", formatKmf(950.0))
        assertEquals("12${nb}500", formatKmf(12_500.0))
        assertEquals("1${nb}240${nb}000", formatKmf(1_240_000.0))
    }

    @Test
    fun arrondiALUniteEtNegatifs() {
        assertEquals("1${nb}500", formatKmf(1_499.6))
        assertEquals("-4${nb}000", formatKmf(-4_000.0))
    }

    @Test
    fun formatCourt() {
        assertEquals("1,24${nb}M", formatKmfCompact(1_240_000.0))
        assertEquals("2${nb}M", formatKmfCompact(2_000_000.0))
        assertEquals("84,5${nb}k", formatKmfCompact(84_500.0))
        assertEquals("9${nb}999", formatKmfCompact(9_999.0))
    }
}
