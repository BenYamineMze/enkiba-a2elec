package com.enkiba.app.ui.users

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserFormTest {

    private val complete = UserForm("Ali", "Said", "3331234", "ali@exemple.km", "motdepasse", isNew = true)

    @Test
    fun creationComplete() = assertNull(complete.validate())

    @Test
    fun champsIdentiteObligatoires() {
        assertEquals("Prénom, nom et téléphone sont obligatoires", complete.copy(phone = " ").validate())
    }

    @Test
    fun emailEtMotDePasseALaCreation() {
        assertEquals("Adresse e-mail invalide", complete.copy(email = "ali@exemple").validate())
        assertEquals("Le mot de passe doit faire au moins 8 caractères", complete.copy(password = "court").validate())
    }

    @Test
    fun modificationMotDePasseFacultatif() {
        val edit = complete.copy(isNew = false, email = "", password = "")
        assertNull(edit.validate())
        assertEquals("Le nouveau mot de passe doit faire au moins 8 caractères", edit.copy(password = "abc").validate())
    }

    @Test
    fun messagesDuServeur() {
        assertEquals("Cette adresse e-mail est déjà utilisée",
            serverErrorMessage(400, """{"error":"create_failed","detail":"A user with this email address has already been registered"}"""))
        assertEquals("Action réservée à l'administrateur de la boutique", serverErrorMessage(403, """{"error":"forbidden"}"""))
        assertEquals("Le serveur a refusé l'opération (code 500)", serverErrorMessage(500, "<html>"))
        assertEquals("Le serveur a refusé l'opération (code 502)", serverErrorMessage(502, null))
        assertEquals("Cette boutique est suspendue", serverErrorMessage(403, """{"error":"shop_suspended"}"""))
        assertEquals("La boutique n'a pas pu être créée", serverErrorMessage(500, """{"error":"shop_failed","detail":"x"}"""))
    }
}
