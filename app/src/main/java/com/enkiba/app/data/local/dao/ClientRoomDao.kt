package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enkiba.app.data.local.entity.ClientEntity

@Dao
interface ClientRoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(client: ClientEntity): Long

    @Query("SELECT * FROM clients WHERE deleted_at IS NULL ORDER BY lastname ASC")
    fun getActive(): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE id = :id")
    fun getById(id: String): ClientEntity?

    @Query("SELECT * FROM clients WHERE sync_status = 'pending'")
    fun getPending(): List<ClientEntity>

    @Query("UPDATE clients SET sync_status = 'synced' WHERE id = :id")
    fun markSynced(id: String)

    @Query("SELECT sync_status FROM clients WHERE id = :id")
    fun getSyncStatus(id: String): String?
}
