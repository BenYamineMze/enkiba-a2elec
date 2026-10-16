package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enkiba.app.data.local.entity.HistoryEntity
import com.enkiba.app.data.local.entity.HistoryWithNames

@Dao
interface HistoryRoomDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(entry: HistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entry: HistoryEntity)

    @Query(
        "SELECT h.*, p.name AS product_name, u.username AS user_name FROM history h " +
            "LEFT JOIN products p ON p.id = h.product_id LEFT JOIN users u ON u.id = h.user_id " +
            "ORDER BY h.timestamp DESC"
    )
    fun getAllWithNames(): List<HistoryWithNames>

    @Query("DELETE FROM history")
    fun clear()

    @Query("SELECT * FROM history WHERE sync_status = 'pending'")
    fun getPending(): List<HistoryEntity>

    @Query("UPDATE history SET sync_status = 'synced' WHERE id = :id")
    fun markSynced(id: String)
}
