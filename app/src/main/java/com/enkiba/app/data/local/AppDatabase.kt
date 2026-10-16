package com.enkiba.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enkiba.app.data.local.dao.ClientRoomDao
import com.enkiba.app.data.local.dao.HistoryRoomDao
import com.enkiba.app.data.local.dao.ProductRoomDao
import com.enkiba.app.data.local.dao.QuoteRoomDao
import com.enkiba.app.data.local.dao.SaleRoomDao
import com.enkiba.app.data.local.dao.StockRoomDao
import com.enkiba.app.data.local.dao.UserRoomDao
import com.enkiba.app.data.local.entity.ClientEntity
import com.enkiba.app.data.local.entity.HistoryEntity
import com.enkiba.app.data.local.entity.ProductEntity
import com.enkiba.app.data.local.entity.QuoteEntity
import com.enkiba.app.data.local.entity.QuoteItemEntity
import com.enkiba.app.data.local.entity.SaleEntity
import com.enkiba.app.data.local.entity.SaleItemEntity
import com.enkiba.app.data.local.entity.SaleReturnEntity
import com.enkiba.app.data.local.entity.StockMovementEntity
import com.enkiba.app.data.local.entity.UserEntity
import com.enkiba.app.utils.SynchronizationManager

/**
 * Base locale Room.
 *
 * Version 11 = première version Room. Les versions 1 à 10 venaient de l'ancien
 * SQLiteOpenHelper (sans utilisateurs réels) : elles sont recréées une dernière fois.
 * À partir de maintenant, toute évolution du schéma passe par une vraie Migration
 * (ajoutée dans MIGRATIONS) et le schéma exporté dans app/schemas.
 */
@Database(
    entities = [
        ProductEntity::class, UserEntity::class, ClientEntity::class, SaleEntity::class,
        SaleItemEntity::class, HistoryEntity::class, StockMovementEntity::class,
        SaleReturnEntity::class, QuoteEntity::class, QuoteItemEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductRoomDao
    abstract fun userDao(): UserRoomDao
    abstract fun clientDao(): ClientRoomDao
    abstract fun saleDao(): SaleRoomDao
    abstract fun historyDao(): HistoryRoomDao
    abstract fun stockDao(): StockRoomDao
    abstract fun quoteDao(): QuoteRoomDao

    companion object {
        const val DATABASE_NAME = "stock.db"

        @Volatile
        private var instance: AppDatabase? = null

        @JvmStatic
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(appContext: Context): AppDatabase =
            Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
                // Anciennes versions SQLiteOpenHelper : recréation (aucune donnée réelle)
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                // TODO étape 6b : à retirer quand les écrans passeront par des ViewModel
                // (accès base hors du thread principal)
                .allowMainThreadQueries()
                .addCallback(object : Callback() {
                    // Base neuve : les curseurs de pull repartent de zéro
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        SynchronizationManager.resetPullCursors(appContext)
                    }

                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        SynchronizationManager.resetPullCursors(appContext)
                    }
                })
                .build()
    }
}
