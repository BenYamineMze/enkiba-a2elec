package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enkiba.app.data.local.entity.ProductEntity
import com.enkiba.app.data.local.entity.ProductWithSold

// Produit + quantité vendue nette (lignes des ventes non supprimées moins les retours)
private const val SELECT_WITH_SOLD =
    "SELECT p.*, " +
        "COALESCE((SELECT SUM(si.quantity) FROM sale_items si JOIN sales s ON s.id = si.sale_id " +
        "WHERE si.product_id = p.id AND s.deleted_at IS NULL), 0) - " +
        "COALESCE((SELECT SUM(r.quantity) FROM sale_returns r JOIN sales s ON s.id = r.sale_id " +
        "WHERE r.product_id = p.id AND s.deleted_at IS NULL), 0) AS sold_quantity " +
        "FROM products p "

@Dao
interface ProductRoomDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(product: ProductEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(product: ProductEntity): Long

    /** Informations du produit (jamais le stock), marquées à envoyer. */
    @Query(
        "UPDATE products SET shop_id = :shopId, name = :name, category = :category, price = :price, " +
            "description = :description, qr_code = :qrCode, initial_stock = :initialStock, " +
            "low_stock_threshold = :lowStockThreshold, updated_at = :now, sync_status = 'pending' WHERE id = :id"
    )
    fun updateDetails(
        id: String, shopId: String?, name: String, category: String, price: Double, description: String?,
        qrCode: String?, initialStock: Int, lowStockThreshold: Int, now: String,
    ): Int

    @Query("UPDATE products SET quantity = quantity + :delta WHERE id = :id")
    fun addQuantity(id: String, delta: Int): Int

    @Query("UPDATE products SET quantity = :quantity WHERE id = :id")
    fun setQuantity(id: String, quantity: Int): Int

    @Query("UPDATE products SET deleted_at = :now, updated_at = :now, sync_status = 'pending' WHERE id = :id")
    fun markDeleted(id: String, now: String): Int

    @Query("DELETE FROM products WHERE id = :id")
    fun deletePhysically(id: String)

    @Query("SELECT * FROM products WHERE id = :id")
    fun getEntity(id: String): ProductEntity?

    @Query(SELECT_WITH_SOLD + "WHERE p.id = :id")
    fun getById(id: String): ProductWithSold?

    @Query(SELECT_WITH_SOLD + "WHERE p.deleted_at IS NULL")
    fun getActive(): List<ProductWithSold>

    @Query(SELECT_WITH_SOLD + "WHERE p.deleted_at IS NULL ORDER BY p.name ASC")
    fun getActiveByName(): List<ProductWithSold>

    @Query(SELECT_WITH_SOLD + "WHERE p.qr_code = :qrCode AND p.deleted_at IS NULL LIMIT 1")
    fun getByQr(qrCode: String): ProductWithSold?

    @Query("SELECT COALESCE(SUM(price * quantity), 0) FROM products WHERE deleted_at IS NULL")
    fun totalStockValue(): Double

    @Query("SELECT COUNT(*) FROM products WHERE deleted_at IS NULL")
    fun countActive(): Int

    @Query("SELECT COUNT(*) FROM products WHERE quantity <= low_stock_threshold AND deleted_at IS NULL")
    fun lowStockCount(): Int

    // --- Synchronisation ---

    @Query(SELECT_WITH_SOLD + "WHERE p.sync_status = 'pending'")
    fun getPending(): List<ProductWithSold>

    @Query("UPDATE products SET sync_status = 'synced' WHERE id = :id")
    fun markSynced(id: String)

    @Query("SELECT sync_status FROM products WHERE id = :id")
    fun getSyncStatus(id: String): String?

    /** Écart local ↔ serveur : mouvements, ventes et retours pas encore envoyés. */
    @Query(
        "SELECT " +
            "COALESCE((SELECT SUM(delta) FROM stock_movements WHERE product_id = :id AND sync_status = 'pending'), 0) - " +
            "COALESCE((SELECT SUM(quantity) FROM sale_items WHERE product_id = :id AND sync_status = 'pending'), 0) + " +
            "COALESCE((SELECT SUM(quantity) FROM sale_returns WHERE product_id = :id AND sync_status = 'pending'), 0)"
    )
    fun pendingLocalDelta(id: String): Int
}
