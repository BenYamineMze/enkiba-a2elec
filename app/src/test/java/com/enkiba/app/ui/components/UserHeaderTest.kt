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
