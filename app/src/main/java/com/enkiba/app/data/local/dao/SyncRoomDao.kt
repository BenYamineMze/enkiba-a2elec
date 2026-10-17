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
}
