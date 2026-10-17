package com.enkiba.app.ui.shop

// Règles de saisie de « Ma boutique » (sans Android : testées en JVM)

data class ShopForm(
    val name: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
) {
    /** Premier problème, ou null si la fiche peut être enregistrée. */
    fun validate(): String? = when {
        name.isBlank() -> "Le nom de la boutique est obligatoire"
        email.isNotBlank() && !EMAIL.matches(email.trim()) -> "Adresse e-mail invalide"
        else -> null
    }

    /** Valeurs envoyées au serveur, sans espaces superflus. */
    fun toFields(): Map<String, Any> = mapOf(
        "name" to name.trim(),
        "address" to address.trim(),
        "phone" to phone.trim(),
        "email" to email.trim(),
    )

    companion object {
        const val MAX_NAME = 60
        const val MAX_ADDRESS = 120
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** Téléphone : chiffres, espaces, « + » et « - » seulement. */
        fun cleanPhone(input: String): String = input.filter { it.isDigit() || it == ' ' || it == '+' || it == '-' }.take(20)
    }
}
