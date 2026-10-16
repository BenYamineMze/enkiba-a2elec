package com.enkiba.app.ui.products

import java.util.UUID

/** Saisie du formulaire produit (ajout ou modification), en texte brut. */
data class ProductForm(
    val name: String = "",
    val category: String = "",
    val quantityText: String = "",
    val priceText: String = "",
    val thresholdText: String = ProductFormRules.DEFAULT_THRESHOLD.toString(),
    val description: String = "",
)

/** Erreurs par champ (null = champ valide). */
data class ProductFormErrors(
    val name: String? = null,
    val category: String? = null,
    val quantity: String? = null,
    val price: String? = null,
    val threshold: String? = null,
) {
    val isValid: Boolean get() = listOf(name, category, quantity, price, threshold).all { it == null }
}

/** Valeurs validées, prêtes à enregistrer. */
data class ValidProduct(
    val name: String,
    val category: String,
    val quantity: Int,
    val price: Double,
    val threshold: Int,
    val description: String,
)

/** Règles du formulaire produit (sans Android : testées en unitaire). */
object ProductFormRules {

    const val DEFAULT_THRESHOLD = 5

    /** Catégories proposées (celles de l'ancien écran d'ajout) ; une autre peut être saisie. */
    val DEFAULT_CATEGORIES = listOf(
        "Appareillage", "Câbles & Fils Électriques", "Éclairage", "Tableaux & Protections",
        "Domotique & Smart Home", "Outils", "Sécurité", "Boîtiers", "Autre",
    )

    /** Code QR stable d'un nouvel article : « PROD-3F2A9C1B ». */
    fun newQrCode(): String = "PROD-" + UUID.randomUUID().toString().substring(0, 8).uppercase()

    /** Prix en KMF : accepte « 1500 », « 1 500 », « 1500,50 ». */
    fun parsePrice(text: String): Double? =
        text.filterNot { it == ' ' || it == '\u202F' || it == '\u00A0' }.replace(',', '.').toDoubleOrNull()

    fun validate(form: ProductForm): Pair<ProductFormErrors, ValidProduct?> {
        val quantity = form.quantityText.trim().toIntOrNull()
        val price = parsePrice(form.priceText.trim())
        val threshold = form.thresholdText.trim().ifEmpty { DEFAULT_THRESHOLD.toString() }.toIntOrNull()
        val errors = ProductFormErrors(
            name = if (form.name.isBlank()) "Le nom est obligatoire" else null,
            category = if (form.category.isBlank()) "Choisissez une catégorie" else null,
            quantity = when {
                quantity == null -> "Quantité invalide"
                quantity < 0 -> "La quantité ne peut pas être négative"
                else -> null
            },
            price = when {
                price == null -> "Prix invalide"
                price < 0 -> "Le prix ne peut pas être négatif"
                else -> null
            },
            threshold = when {
                threshold == null -> "Seuil invalide"
                threshold < 0 -> "Le seuil ne peut pas être négatif"
                else -> null
            },
        )
        if (!errors.isValid) return errors to null
        return errors to ValidProduct(
            name = form.name.trim(),
            category = form.category.trim(),
            quantity = quantity!!,
            price = price!!,
            threshold = threshold!!,
            description = form.description.trim(),
        )
    }

    /**
     * Stock de référence (jauge) après modification : remis au nouveau stock seulement
     * si la quantité change ; une simple modification de prix ne touche pas la jauge.
     */
    fun newInitialStock(oldInitial: Int, oldQuantity: Int, newQuantity: Int): Int =
        if (newQuantity != oldQuantity) newQuantity else oldInitial

    /** Prix affiché dans le champ : « 1500 » plutôt que « 1500.0 ». */
    fun priceText(price: Double): String = if (price % 1.0 == 0.0) price.toLong().toString() else price.toString()
}
