package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.enkiba.app.data.local.entity.ChatMessageEntity
import com.enkiba.app.data.local.entity.SYNC_PENDING

/** Discussion de la boutique. */
@Dao
interface ChatRoomDao {

    /** Messages visibles, du plus ancien au plus récent. */
    @Query("SELECT * FROM chat_messages WHERE deleted_at IS NULL ORDER BY created_at, id")
    fun visible(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    fun get(id: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE sync_status = 'pending' ORDER BY created_at")
    fun pending(): List<ChatMessageEntity>

    @Query("UPDATE chat_messages SET sync_status = 'synced' WHERE id = :id")
    fun markSynced(id: String)

    /** Message reçu du serveur ; un retrait local pas encore envoyé garde la priorité. */
    @Transaction
    fun applyRemote(message: ChatMessageEntity) {
        val local = get(message.id)
        if (local?.syncStatus == SYNC_PENDING && local.deletedAt != null) return
        upsert(message)
    }

    @Query("UPDATE chat_messages SET deleted_at = :at, sync_status = 'pending' WHERE id = :id")
    fun markDeleted(id: String, at: String)

    /** Messages des autres arrivés après la dernière lecture. */
    @Query(
        "SELECT COUNT(*) FROM chat_messages WHERE deleted_at IS NULL AND sender_id <> :me " +
            "AND (:lastRead IS NULL OR created_at > :lastRead)"
    )
    fun unread(me: String, lastRead: String?): Int

    /** Purge locale, alignée sur celle du serveur (90 jours) ; jamais un message pas encore envoyé. */
    @Query("DELETE FROM chat_messages WHERE created_at < :before AND sync_status = 'synced'")
    fun purgeBefore(before: String)
}
