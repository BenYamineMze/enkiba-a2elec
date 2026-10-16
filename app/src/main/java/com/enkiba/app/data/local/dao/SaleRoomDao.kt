package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enkiba.app.data.local.entity.SaleEntity
import com.enkiba.app.data.local.entity.SaleItemEntity
import com.enkiba.app.data.local.entity.SaleItemWithReturned
import com.enkiba.app.data.local.entity.SaleWithExtras

// Vente + nom du vendeur (cache local users) + montant remboursé (retours)
private const val SELECT_WITH_EXTRAS =
    "SELECT s.*, TRIM(COALESCE(u.username, '') || ' ' || COALESCE(u.lastname, '')) AS seller_name, " +
        "COALESCE((SELECT SUM(r.refund_amount) FROM sale_returns r WHERE r.sale_id = s.id), 0) AS refunded " +
        "FROM sales s LEFT JOIN users u ON u.id = s.seller_id "

@Dao
interface SaleRoomDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSale(sale: SaleEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertItem(item: SaleItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertItem(item: SaleItemEntity)

    @Query(SELECT_WITH_EXTRAS + "WHERE s.id = :id")
    fun getById(id: String): SaleWithExtras?

    @Query(SELECT_WITH_EXTRAS + "WHERE s.deleted_at IS NULL ORDER BY s.sale_date DESC")
    fun getActive(): List<SaleWithExtras>

    @Query(SELECT_WITH_EXTRAS + "WHERE s.seller_id = :sellerId AND s.deleted_at IS NULL ORDER BY s.sale_date DESC")
    fun getActiveBySeller(sellerId: String): List<SaleWithExtras>

    @Query(
        SELECT_WITH_EXTRAS + "WHERE s.payment_method = :paymentMethod AND s.deleted_at IS NULL " +
            "ORDER BY s.sale_date DESC LIMIT :limit"
    )
    fun getRecentByPayment(paymentMethod: String, limit: Int): List<SaleWithExtras>

    /** Montant encaissé (total moins remboursements) ; paymentMethod null = tous. */
    @Query(
        "SELECT COALESCE(SUM(s.total_price - COALESCE((SELECT SUM(r.refund_amount) FROM sale_returns r " +
            "WHERE r.sale_id = s.id), 0)), 0) FROM sales s " +
            "WHERE s.deleted_at IS NULL AND (:paymentMethod IS NULL OR s.payment_method = :paymentMethod)"
    )
    fun netTotal(paymentMethod: String?): Double

    // --- Accueil : période [start, end[ en horodatage UTC « yyyy-MM-ddTHH:mm » (comparaison de chaînes)
    //     sellerId null = tous les vendeurs, paymentMethod null = tous les modes

    @Query(
        "SELECT COALESCE(SUM(s.total_price - COALESCE((SELECT SUM(r.refund_amount) FROM sale_returns r " +
            "WHERE r.sale_id = s.id), 0)), 0) FROM sales s " +
            "WHERE s.deleted_at IS NULL AND s.sale_date >= :start AND s.sale_date < :end " +
            "AND (:sellerId IS NULL OR s.seller_id = :sellerId) " +
            "AND (:paymentMethod IS NULL OR s.payment_method = :paymentMethod)"
    )
    fun netTotalBetween(start: String, end: String, sellerId: String?, paymentMethod: String?): Double

    @Query(
        "SELECT COUNT(*) FROM sales s WHERE s.deleted_at IS NULL AND s.sale_date >= :start AND s.sale_date < :end " +
            "AND (:sellerId IS NULL OR s.seller_id = :sellerId)"
    )
    fun countBetween(start: String, end: String, sellerId: String?): Int

    @Query(
        SELECT_WITH_EXTRAS + "WHERE s.deleted_at IS NULL AND (:sellerId IS NULL OR s.seller_id = :sellerId) " +
            "ORDER BY s.sale_date DESC LIMIT :limit"
    )
    fun getRecent(sellerId: String?, limit: Int): List<SaleWithExtras>

    @Query(
        "SELECT si.*, COALESCE((SELECT SUM(r.quantity) FROM sale_returns r WHERE r.sale_item_id = si.id), 0) " +
            "AS returned FROM sale_items si WHERE si.sale_id = :saleId ORDER BY si.rowid"
    )
    fun getItemsWithReturned(saleId: String): List<SaleItemWithReturned>

    @Query("SELECT * FROM sale_items WHERE id = :id")
    fun getItem(id: String): SaleItemEntity?

    @Query("SELECT * FROM sale_items WHERE sale_id = :saleId")
    fun getItems(saleId: String): List<SaleItemEntity>

    @Query("DELETE FROM sale_items WHERE sale_id = :saleId")
    fun deleteItems(saleId: String)

    @Query("DELETE FROM sales WHERE id = :id")
    fun deleteSale(id: String)

    // --- Synchronisation ---

    @Query("SELECT * FROM sales WHERE sync_status = 'pending'")
    fun getPendingSales(): List<SaleEntity>

    @Query("SELECT * FROM sale_items WHERE sync_status = 'pending'")
    fun getPendingItems(): List<SaleItemEntity>

    @Query("UPDATE sales SET sync_status = 'synced' WHERE id = :id")
    fun markSaleSynced(id: String)

    @Query("UPDATE sale_items SET sync_status = 'synced' WHERE id = :id")
    fun markItemSynced(id: String)
}
