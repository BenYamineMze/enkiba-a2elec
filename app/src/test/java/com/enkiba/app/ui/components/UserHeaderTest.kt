package com.enkiba.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class UserHeaderTest {

    @Test
    fun initiales() {
        assertEquals("MB", initialsOf("Mze", "Ben"))
        assertEquals("M", initialsOf(" mze ", null))
        assertEquals("?", initialsOf("", "  "))
    }

    @Test
    fun dateDuJourEnFrancais() {
        val date = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 16) }.time
        assertEquals("Vendredi 16 octobre", todayLabel(date))
    }
}

class DisplayNameTest {
    @org.junit.Test
    fun prenomDuProfilSinonEmail() {
        org.junit.Assert.assertEquals("Mze", displayFirstName("Mze", "autre@x.km"))
        org.junit.Assert.assertEquals("Mze", displayFirstName("", "mze.ben.km@gmail.com"))
        org.junit.Assert.assertEquals("Vendeur", displayFirstName(null, "vendeur_2@a2elec.km"))
        org.junit.Assert.assertNull(displayFirstName(" ", "123@x.km"))
        org.junit.Assert.assertNull(displayFirstName(null, null))
    }
}
