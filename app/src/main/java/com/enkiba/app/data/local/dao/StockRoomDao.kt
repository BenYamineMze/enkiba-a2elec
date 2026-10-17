package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enkiba.app.data.local.entity.SaleReturnEntity
import com.enkiba.app.data.local.entity.StockMovementEntity

/** Mouvements de stock saisis localement et retours d'articles. */
@Dao
interface StockRoomDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMovement(movement: StockMovementEntity): Long

    @Query("SELECT * FROM stock_movements WHERE sync_status = 'pending' ORDER BY created_at")
    fun getPendingMovements(): List<StockMovementEntity>

    @Query("UPDATE stock_movements SET sync_status = 'synced' WHERE id = :id")
    fun markMovementSynced(id: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertReturn(ret: SaleReturnEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertReturn(ret: SaleReturnEntity)

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM sale_returns WHERE sale_item_id = :saleItemId")
    fun returnedQuantity(saleItemId: String): Int

    @Query("DELETE FROM sale_returns WHERE sale_id = :saleId")
    fun deleteReturnsOfSale(saleId: String)

    @Query("SELECT * FROM sale_returns WHERE sync_status = 'pending' ORDER BY created_at")
    fun getPendingReturns(): List<SaleReturnEntity>

    @Query("UPDATE sale_returns SET sync_status = 'synced' WHERE id = :id")
    fun markReturnSynced(id: String)
}
