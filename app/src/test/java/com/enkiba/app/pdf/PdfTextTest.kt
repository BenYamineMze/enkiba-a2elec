package com.enkiba.app.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PdfTextTest {

    private val zone = TimeZone.getTimeZone("Indian/Comoro")
    private val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }.parse("2026-10-17 14:32")!!

    // Mesure simple : 1 point par caractère
    private val measure: (String) -> Float = { it.length.toFloat() }

    @Test
    fun initiales() {
        assertEquals("A2", PdfText.initials("A2elec"))
        assertEquals("QP", PdfText.initials("Quincaillerie du Port"))
        assertEquals("MÉ", PdfText.initials("Moroni-Électricité"))
        assertEquals("?", PdfText.initials("  "))
    }

    @Test
    fun decoupageEnLignes() {
        assertEquals(listOf("Disjoncteur", "16A Legrand"), PdfText.wrap("Disjoncteur 16A Legrand", 12f, measure))
        // Mot trop long coupé, puis limite de lignes avec points de suspension
        assertEquals(listOf("abcde", "fghij"), PdfText.wrap("abcdefghij", 5f, measure))
        val limited = PdfText.wrap("un deux trois quatre cinq", 9f, measure, maxLines = 2)
        assertEquals(2, limited.size)
        assertTrue(limited.last().endsWith("…"))
        assertEquals(listOf(""), PdfText.wrap("", 10f, measure))
    }

    @Test
    fun dates() {
        assertEquals("17 octobre 2026, 14:32", PdfText.longDateTime(date, zone))
        assertEquals("31/10/2026", PdfText.shortDate("2026-10-31"))
        assertEquals("bientôt", PdfText.shortDate("bientôt"))
    }

    @Test
    fun nomsDeFichiers() {
        assertEquals("Facture_A1B2C3_2026-10-17.pdf", PdfText.invoiceFileName("A1B2C3", date, zone))
        assertEquals("Devis_DEV-0001_2026-10-17.pdf", PdfText.quoteFileName("DEV-0001", date, zone))
        assertEquals("Ventes_2026-10-17_1432.pdf", PdfText.reportFileName("Ventes", date, zone))
        assertEquals("Ref-avec-espaces", PdfText.safeFilePart(" Ref avec/espaces "))
    }
}
