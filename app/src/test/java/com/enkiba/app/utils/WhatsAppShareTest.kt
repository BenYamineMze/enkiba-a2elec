package com.enkiba.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppShareTest {

    @Test
    fun numeroLocalComorien() {
        assertEquals("2693212345", WhatsAppShare.normalizePhone("321 23 45"))
        assertEquals("2693212345", WhatsAppShare.normalizePhone("269 321 23 45"))
    }

    @Test
    fun numeroInternational() {
        assertEquals("33612345678", WhatsAppShare.normalizePhone("+33 6 12 34 56 78"))
        assertEquals("2693212345", WhatsAppShare.normalizePhone("00269 321-23-45"))
    }

    @Test
    fun numeroAmbiguOuVide() {
        assertNull(WhatsAppShare.normalizePhone("06 12 34 56 78")) // pays inconnu : choix du contact dans WhatsApp
        assertNull(WhatsAppShare.normalizePhone(""))
        assertNull(WhatsAppShare.normalizePhone(null))
        assertNull(WhatsAppShare.normalizePhone("+12"))
    }

    @Test
    fun messageFacture() {
        assertEquals(
            "Bonjour Ali, voici votre facture n° A1B2C3 de A2elec, d'un montant de 12 500 KMF. Merci pour votre achat !",
            WhatsAppShare.invoiceMessage("Ali", "A1B2C3", 12500.0, "A2elec"),
        )
        assertEquals(true, WhatsAppShare.invoiceMessage(" ", "X", 1.0, "B").startsWith("Bonjour, voici"))
    }

    @Test
    fun messageDevisAvecValidite() {
        assertEquals(
            "Bonjour, voici votre devis DEV-0001 de A2elec, d'un montant de 1 500 KMF. Il est valable jusqu'au 31/10/2026.",
            WhatsAppShare.quoteMessage(null, "DEV-0001", 1500.0, "A2elec", "2026-10-31"),
        )
    }
}
