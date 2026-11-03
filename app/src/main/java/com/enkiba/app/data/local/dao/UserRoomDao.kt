package com.enkiba.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.enkiba.app.data.local.entity.UserEntity

@Dao
interface UserRoomDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    fun getById(id: String): UserEntity?

    @Query("SELECT * FROM users")
    fun getAll(): List<UserEntity>

    /** Ajoute un membre inconnu (nom des expéditeurs du chat) sans toucher aux fiches existantes. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfMissing(user: UserEntity)
}
