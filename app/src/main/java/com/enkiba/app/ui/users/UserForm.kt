package com.enkiba.app.ui.users

import com.google.gson.JsonParser

// Règles du formulaire vendeur et messages d'erreur du serveur (sans Android : testables en JVM)

data class UserForm(
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val password: String = "",
    /** Création (e-mail et mot de passe obligatoires) ou modification (mot de passe facultatif). */
    val isNew: Boolean = true,
) {
    /** Premier problème rencontré, ou null si le formulaire peut être envoyé. */
    fun validate(): String? = when {
        firstName.isBlank() || lastName.isBlank() || phone.isBlank() -> "Prénom, nom et téléphone sont obligatoires"
        isNew && !EMAIL.matches(email.trim()) -> "Adresse e-mail invalide"
        isNew && password.length < MIN_PASSWORD -> "Le mot de passe doit faire au moins $MIN_PASSWORD caractères"
        !isNew && password.isNotEmpty() && password.length < MIN_PASSWORD ->
            "Le nouveau mot de passe doit faire au moins $MIN_PASSWORD caractères"
        else -> null
    }

    companion object {
        const val MIN_PASSWORD = 8
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/** Message lisible pour une réponse en erreur des fonctions create-seller / update-seller / set-seller-status. */
fun serverErrorMessage(code: Int, body: String?): String {
    val json = runCatching { JsonParser.parseString(body.orEmpty()).asJsonObject }.getOrNull()
    val error = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
    val detail = json?.get("detail")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().lowercase()
    return when {
        error == "create_failed" && ("already" in detail || "registered" in detail || "exists" in detail) ->
            "Cette adresse e-mail est déjà utilisée"
        error == "forbidden" || code == 403 -> "Action réservée à l'administrateur de la boutique"
        error == "unauthorized" || code == 401 -> "Session expirée : reconnectez-vous"
        error == "invalid_input" -> "Informations invalides"
        else -> "Le serveur a refusé l'opération (code $code)"
    }
}
