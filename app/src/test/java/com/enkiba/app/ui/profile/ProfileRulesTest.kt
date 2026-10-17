package com.enkiba.app.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {

    @Test
    fun telephoneNettoye() {
        assertEquals("+269 333 00 00", ProfileRules.cleanPhone("+269 333 00 00"))
        assertEquals("269-33", ProfileRules.cleanPhone("26a9-3(3)"))
        assertEquals(20, ProfileRules.cleanPhone("1".repeat(40)).length)
    }

    @Test
    fun nomLimite() {
        assertEquals(ProfileRules.MAX_NAME, ProfileRules.cleanName("x".repeat(80)).length)
    }

    @Test
    fun prenomObligatoire() {
        assertTrue(ProfileUiState(firstName = "  ").firstNameMissing)
        assertFalse(ProfileUiState(firstName = "Mze").firstNameMissing)
    }
}
