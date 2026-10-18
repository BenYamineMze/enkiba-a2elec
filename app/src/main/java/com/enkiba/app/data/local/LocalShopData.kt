package com.enkiba.app.data.local

import android.content.Context
import androidx.core.content.edit
import com.enkiba.app.utils.SynchronizationManager

/**
 * La base locale ne contient qu'une boutique à la fois. Ce suivi garantit qu'on ne mélange
 * jamais deux boutiques sur un téléphone (changement de boutique, autre compte qui se connecte).
 * À appeler hors du thread principal.
 */
object LocalShopData {

    private const val PREFS = "enkiba_local_data"
    private const val KEY_SHOP = "shop_id"

    /** Boutique des données locales, ou null si la base est vide. */
    fun owner(context: Context): String? =
        prefs(context).getString(KEY_SHOP, null) ?: AppDatabase.get(context).syncDao().anyShopId()

    fun pendingChanges(context: Context): Int = AppDatabase.get(context).syncDao().countPending()

    /**
     * Prépare la base locale pour [shopId]. Si elle contient une autre boutique, elle est vidée
     * (avec les curseurs de synchronisation) — sauf s'il reste des changements non envoyés :
     * on renvoie alors false et rien n'est effacé.
     */
    fun switchTo(context: Context, shopId: String): Boolean {
        val current = owner(context)
        if (current == shopId) {
            remember(context, shopId)
            return true
        }
        if (current != null && pendingChanges(context) > 0) return false
        AppDatabase.get(context).clearAllTables()
        SynchronizationManager.resetPullCursors(context)
        remember(context, shopId)
        return true
    }

    private fun remember(context: Context, shopId: String) = prefs(context).edit { putString(KEY_SHOP, shopId) }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
