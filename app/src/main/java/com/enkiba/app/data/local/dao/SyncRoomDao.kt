package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query

/** État global de la synchronisation (pastille de l'accueil). */
@Dao
interface SyncRoomDao {
    /** Nombre de lignes modifiées sur ce téléphone et pas encore envoyées. */
    @Query(
        "SELECT (SELECT COUNT(*) FROM products WHERE sync_status = 'pending') + " +
            "(SELECT COUNT(*) FROM clients WHERE sync_status = 'pending') + " +
            "(SELECT COUNT(*) FROM sales WHERE sync_status = 'pending') + " +
            "(SELECT COUNT(*) FROM sale_items WHERE sync_status = 'pending') + " +
            "(SELECT COUNT(*) FROM sale_returns WHERE sync_status = 'pending') + " +
            "(SELECT COUNT(*) FROM stock_movements WHERE sync_status = 'pending') + " +
            "(SELECT COUNT(*) FROM quotes WHERE sync_status = 'pending') + " +
            "(SELECT COUNT(*) FROM quote_items WHERE sync_status = 'pending') + " +
            "(SELECT COUNT(*) FROM history WHERE sync_status = 'pending')"
    )
    fun countPending(): Int

    /** Boutique des données locales (pour les bases créées avant le suivi de la boutique). */
    @Query(
        "SELECT shop_id FROM products WHERE shop_id IS NOT NULL UNION ALL " +
            "SELECT shop_id FROM sales WHERE shop_id IS NOT NULL UNION ALL " +
            "SELECT shop_id FROM clients WHERE shop_id IS NOT NULL LIMIT 1"
    )
    fun anyShopId(): String?
}
