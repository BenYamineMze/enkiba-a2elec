package com.enkiba.app.ui.shop

import com.enkiba.app.data.local.ShopDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopFormTest {

    @Test
    fun nomSeulSuffit() = assertNull(ShopForm(name = "A2elec").validate())

    @Test
    fun nomObligatoireEtEmailVerifie() {
        assertEquals("Le nom de la boutique est obligatoire", ShopForm(name = "  ").validate())
        assertEquals("Adresse e-mail invalide", ShopForm(name = "A2elec", email = "contact@a2elec").validate())
        assertNull(ShopForm(name = "A2elec", email = "contact@a2elec.km").validate())
    }

    @Test
    fun valeursEnvoyeesSansEspaces() {
        val fields = ShopForm(" A2elec ", " Moroni ", " 269 410 06 07 ", " contact@a2elec.km ").toFields()
        assertEquals(mapOf("name" to "A2elec", "address" to "Moroni", "phone" to "269 410 06 07", "email" to "contact@a2elec.km"), fields)
    }

    @Test
    fun telephoneNettoye() = assertEquals("+269 410-06 07", ShopForm.cleanPhone("+269 410-06 07abc"))

    @Test
    fun piedDePageSansChampVide() {
        assertEquals("A2elec • 269 410 06 07", ShopDetails("A2elec", phone = "269 410 06 07").footerLine)
        assertFalse(ShopDetails("A2elec").hasContact)
        assertTrue(ShopDetails("A2elec", address = "Moroni").hasContact)
    }
}
