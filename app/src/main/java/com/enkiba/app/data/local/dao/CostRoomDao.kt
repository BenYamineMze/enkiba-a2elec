package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.enkiba.app.data.local.entity.MarginRow
import com.enkiba.app.data.local.entity.ProductCostEntity
import com.enkiba.app.data.local.entity.SYNC_PENDING
import com.enkiba.app.data.local.entity.SYNC_SYNCED
import com.enkiba.app.data.local.entity.SaleItemCostEntity

/** Prix d'achat et marges (admins). Les vendeurs n'ont jamais ces lignes sur leur téléphone. */
@Dao
interface CostRoomDao {

    @Query("SELECT * FROM product_costs WHERE product_id = :productId")
    fun get(productId: String): ProductCostEntity?

    @Upsert
    fun upsert(cost: ProductCostEntity)

    @Query("SELECT * FROM product_costs WHERE sync_status = 'pending'")
    fun pending(): List<ProductCostEntity>

    /** Marqué envoyé seulement si le prix n'a pas changé entre-temps sur le téléphone. */
    @Query("UPDATE product_costs SET sync_status = 'synced' WHERE product_id = :productId AND cost_price = :costPrice")
    fun markSynced(productId: String, costPrice: Double)

    /** Ligne reçue du serveur ; une modification locale pas encore envoyée garde la priorité. */
    @Transaction
    fun applyRemote(cost: ProductCostEntity) {
        if (get(cost.productId)?.syncStatus == SYNC_PENDING) return
        upsert(cost.copy(syncStatus = SYNC_SYNCED))
    }

    @Upsert
    fun upsertSaleItemCost(cost: SaleItemCostEntity)

    /** L'utilisateur n'est plus admin : aucun prix d'achat ne doit rester sur le téléphone. */
    @Transaction
    fun clearAll() {
        clearProductCosts()
        clearSaleItemCosts()
    }

    @Query("DELETE FROM product_costs")
    fun clearProductCosts()

    @Query("DELETE FROM sale_item_costs")
    fun clearSaleItemCosts()

    /** Valeur du stock au prix d'achat (produits qui en ont un). */
    @Query(
        "SELECT COALESCE(SUM(p.quantity * c.cost_price), 0) FROM products p " +
            "JOIN product_costs c ON c.product_id = p.id WHERE p.deleted_at IS NULL AND p.quantity > 0"
    )
    fun stockValueAtCost(): Double

    @Query(
        "SELECT p.id FROM products p WHERE p.deleted_at IS NULL " +
            "AND NOT EXISTS (SELECT 1 FROM product_costs c WHERE c.product_id = p.id)"
    )
    fun productIdsWithoutCost(): List<String>

    /** Lignes de vente de la période (ventes non annulées), nettes des retours. */
    @Query(
        "SELECT si.product_id, si.product_name, " +
            "si.total_price - COALESCE((SELECT SUM(r.refund_amount) FROM sale_returns r WHERE r.sale_item_id = si.id), 0) AS revenue, " +
            "si.quantity - COALESCE((SELECT SUM(r.quantity) FROM sale_returns r WHERE r.sale_item_id = si.id), 0) AS sold, " +
            "c.cost_price FROM sale_items si JOIN sales s ON s.id = si.sale_id " +
            "LEFT JOIN sale_item_costs c ON c.sale_item_id = si.id " +
            "WHERE s.deleted_at IS NULL AND s.sale_date >= :start AND s.sale_date < :end"
    )
    fun marginRowsBetween(start: String, end: String): List<MarginRow>
}
