package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.enkiba.app.data.local.entity.InventoryCountEntity
import com.enkiba.app.data.local.entity.InventorySessionEntity
import com.enkiba.app.data.local.entity.SYNC_PENDING

/** Inventaire : session ouverte et comptages. */
@Dao
interface InventoryRoomDao {

    @Query("SELECT * FROM inventory_sessions WHERE status = 'open' ORDER BY created_at DESC LIMIT 1")
    fun openSession(): InventorySessionEntity?

    @Query("SELECT * FROM inventory_sessions WHERE id = :id")
    fun session(id: String): InventorySessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSession(session: InventorySessionEntity)

    @Query("SELECT * FROM inventory_sessions WHERE sync_status = 'pending'")
    fun pendingSessions(): List<InventorySessionEntity>

    @Query("UPDATE inventory_sessions SET sync_status = 'synced' WHERE id = :id")
    fun markSessionSynced(id: String)

    /** Session reçue du serveur ; une session créée ici et pas encore envoyée reste telle quelle. */
    @Transaction
    fun applyRemoteSession(session: InventorySessionEntity) {
        if (session(session.id)?.syncStatus == SYNC_PENDING) return
        upsertSession(session)
    }

    /** Session close à l'instant par ce téléphone (validation ou annulation acceptée par le serveur). */
    @Query("UPDATE inventory_sessions SET status = :status, closed_at = :closedAt WHERE id = :id")
    fun close(id: String, status: String, closedAt: String)

    @Query("SELECT * FROM inventory_counts WHERE session_id = :sessionId")
    fun counts(sessionId: String): List<InventoryCountEntity>

    @Query("SELECT * FROM inventory_counts WHERE session_id = :sessionId AND product_id = :productId")
    fun count(sessionId: String, productId: String): InventoryCountEntity?

    /** REPLACE : un seul comptage par article et par session (index unique). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertCount(count: InventoryCountEntity)

    @Query("SELECT * FROM inventory_counts WHERE sync_status = 'pending'")
    fun pendingCounts(): List<InventoryCountEntity>

    /** Marqué envoyé seulement si le comptage n'a pas changé entre-temps. */
    @Query("UPDATE inventory_counts SET sync_status = 'synced' WHERE id = :id AND counted = :counted")
    fun markCountSynced(id: String, counted: Int)

    /** Comptage reçu du serveur ; un comptage local pas encore envoyé garde la priorité. */
    @Transaction
    fun applyRemoteCount(count: InventoryCountEntity) {
        if (count(count.sessionId, count.productId)?.syncStatus == SYNC_PENDING) return
        upsertCount(count)
    }

    @Query("SELECT COUNT(*) FROM inventory_counts WHERE session_id = :sessionId AND sync_status = 'pending'")
    fun pendingCountsIn(sessionId: String): Int
}
