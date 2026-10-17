package com.enkiba.app.data.local

import android.content.Context
import androidx.core.content.edit
import com.enkiba.app.models.Shop
import com.enkiba.app.utils.SessionManager

/** Nom et coordonnées d'une boutique, imprimés sur les factures et les devis. */
data class ShopDetails(
    val name: String,
    val address: String = "",
    val phone: String = "",
    val email: String = "",
) {
    /** Pied de page des PDF : seules les informations renseignées. */
    val footerLine: String
        get() = listOf(name, address, phone, email).filter { it.isNotBlank() }.joinToString(" • ")

    /** Coordonnées complètes (au moins une adresse ou un téléphone) : sinon on invite l'admin à les saisir. */
    val hasContact: Boolean get() = address.isNotBlank() || phone.isNotBlank()

    companion object {
        /** Avant la première synchro : un nom neutre plutôt qu'une boutique précise. */
        val UNKNOWN = ShopDetails(name = "Ma boutique")
    }
}

/**
 * Cache local des coordonnées de la boutique (une boutique par session), pour imprimer
 * hors ligne. Rempli à la connexion, à chaque synchro et après « Ma boutique ».
 * Clé par boutique : un changement de compte ne réutilise jamais les coordonnées d'une autre.
 */
object ShopStore {

    private const val PREFS = "enkiba_shop"

    @JvmStatic
    fun save(context: Context, shop: Shop) {
        val id = shop.id ?: return
        prefs(context).edit {
            putString("$id.name", shop.name.orEmpty())
            putString("$id.address", shop.address.orEmpty())
            putString("$id.phone", shop.phone.orEmpty())
            putString("$id.email", shop.email.orEmpty())
        }
    }

    @JvmStatic
    fun get(context: Context, shopId: String?): ShopDetails {
        if (shopId == null) return ShopDetails.UNKNOWN
        val p = prefs(context)
        val name = p.getString("$shopId.name", null) ?: return ShopDetails.UNKNOWN
        return ShopDetails(
            name = name.ifBlank { ShopDetails.UNKNOWN.name },
            address = p.getString("$shopId.address", "").orEmpty(),
            phone = p.getString("$shopId.phone", "").orEmpty(),
            email = p.getString("$shopId.email", "").orEmpty(),
        )
    }

    /** Coordonnées de la boutique de l'utilisateur connecté. */
    @JvmStatic
    fun current(context: Context): ShopDetails = get(context, SessionManager(context.applicationContext).shopId)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
