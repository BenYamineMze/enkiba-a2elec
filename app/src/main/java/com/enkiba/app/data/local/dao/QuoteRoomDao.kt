package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enkiba.app.data.local.entity.QuoteEntity
import com.enkiba.app.data.local.entity.QuoteItemEntity
import com.enkiba.app.data.local.entity.QuoteWithClient

// Devis + nom du client
private const val SELECT_WITH_CLIENT =
    "SELECT q.*, TRIM(COALESCE(c.firstname, '') || ' ' || COALESCE(c.lastname, '')) AS client_name " +
        "FROM quotes q LEFT JOIN clients c ON c.id = q.client_id "

@Dao
interface QuoteRoomDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertQuote(quote: QuoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertItem(item: QuoteItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertQuote(quote: QuoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertItem(item: QuoteItemEntity)

    @Query(SELECT_WITH_CLIENT + "WHERE q.deleted_at IS NULL ORDER BY q.created_at DESC")
    fun getActive(): List<QuoteWithClient>

    @Query(SELECT_WITH_CLIENT + "WHERE q.seller_id = :sellerId AND q.deleted_at IS NULL ORDER BY q.created_at DESC")
    fun getActiveBySeller(sellerId: String): List<QuoteWithClient>

    @Query(SELECT_WITH_CLIENT + "WHERE q.id = :id")
    fun getById(id: String): QuoteWithClient?

    /** Devis en cours : brouillon ou accepté, non supprimé, encore valable (today = yyyy-MM-dd). */
    @Query(
        "SELECT COUNT(*) FROM quotes WHERE deleted_at IS NULL AND status IN ('draft', 'accepted') " +
            "AND (valid_until IS NULL OR valid_until >= :today) AND (:sellerId IS NULL OR seller_id = :sellerId)"
    )
    fun countOpen(today: String, sellerId: String?): Int

    @Query("SELECT * FROM quote_items WHERE quote_id = :quoteId ORDER BY rowid")
    fun getItems(quoteId: String): List<QuoteItemEntity>

    @Query("UPDATE quotes SET status = :status, updated_at = :now, sync_status = 'pending' WHERE id = :id AND status != 'converted'")
    fun updateStatus(id: String, status: String, now: String): Int

    @Query("UPDATE quotes SET deleted_at = :now, updated_at = :now, sync_status = 'pending' WHERE id = :id AND status != 'converted'")
    fun markDeleted(id: String, now: String): Int

    /** Conversion en vente : uniquement depuis un devis encore convertible. */
    @Query(
        "UPDATE quotes SET status = 'converted', converted_sale_id = :saleId, updated_at = :now, sync_status = 'pending' " +
            "WHERE id = :id AND deleted_at IS NULL AND status NOT IN ('converted', 'rejected')"
    )
    fun markConverted(id: String, saleId: String, now: String): Int

    @Query("UPDATE quotes SET sync_status = 'synced' WHERE id = :id")
    fun markQuoteSynced(id: String)

    @Query("UPDATE quote_items SET sync_status = 'synced' WHERE id = :id")
    fun markItemSynced(id: String)

    @Query("SELECT sync_status FROM quotes WHERE id = :id")
    fun getSyncStatus(id: String): String?

    @Query("SELECT * FROM quotes WHERE sync_status = 'pending' ORDER BY created_at")
    fun getPendingQuotes(): List<QuoteEntity>

    @Query("SELECT * FROM quote_items WHERE sync_status = 'pending'")
    fun getPendingItems(): List<QuoteItemEntity>
}
