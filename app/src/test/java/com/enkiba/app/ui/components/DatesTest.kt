package com.enkiba.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

class DatesTest {

    private val comoros = TimeZone.getTimeZone("Indian/Comoro") // UTC+3, sans heure d'été
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun lesDeuxFormatsDeDateSontLus() {
        val local = parseIsoDate("2026-10-16T10:09Z")
        val server = parseIsoDate("2026-10-16T10:09:06.123456+00:00")
        assertNotNull(local); assertNotNull(server)
        assertEquals(local!!.time + 6_000, server!!.time) // 6 s plus tard ; fractions de seconde ignorées
        assertNull(parseIsoDate("pas une date"))
        assertNull(parseIsoDate(null))
    }

    @Test
    fun jourLocalAuxComoresEnBornesUtc() {
        val day = parseIsoDate("2026-10-16T08:00Z")!!
        assertEquals("2026-10-15T21:00" to "2026-10-16T21:00", localDayRangeUtc(day, comoros))
        assertEquals("2026-10-14T21:00" to "2026-10-15T21:00", localDayRangeUtc(day, comoros, offsetDays = -1))
    }

    @Test
    fun uneVenteDuSoirTombeDansLeBonJour() {
        // 22:30 heure des Comores = 19:30 UTC : toujours le 16 en local
        val (start, end) = localDayRangeUtc(parseIsoDate("2026-10-16T08:00Z")!!, comoros)
        val sale = "2026-10-16T19:30:00+00:00"
        assert(sale >= start && sale < end)
        // 00:30 le 17 en local = 21:30 UTC le 16 : hors du jour
        assert(!("2026-10-16T21:30Z" < end))
    }

    @Test
    fun momentDeVente() {
        val now = parseIsoDate("2026-10-16T12:00Z")!!
        assertEquals("14:32", formatSaleMoment("2026-10-16T11:32Z", now, comoros))
        assertEquals("15/10 · 14:32", formatSaleMoment("2026-10-15T11:32:00+00:00", now, comoros))
        assertEquals("16/10 · 11:32", formatSaleMoment("2026-10-16T11:32Z", parseIsoDate("2026-10-17T12:00Z")!!, utc))
    }
}
