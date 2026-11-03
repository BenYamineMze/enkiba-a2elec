package com.enkiba.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enkiba.app.data.local.dao.ClientRoomDao
import com.enkiba.app.data.local.dao.CostRoomDao
import com.enkiba.app.data.local.dao.ExportRoomDao
import com.enkiba.app.data.local.dao.HistoryRoomDao
import com.enkiba.app.data.local.dao.InventoryRoomDao
import com.enkiba.app.data.local.dao.ProductRoomDao
import com.enkiba.app.data.local.dao.QuoteRoomDao
import com.enkiba.app.data.local.dao.SaleRoomDao
import com.enkiba.app.data.local.dao.StockRoomDao
import com.enkiba.app.data.local.dao.SyncRoomDao
import com.enkiba.app.data.local.dao.UserRoomDao
import com.enkiba.app.data.local.entity.ClientEntity
import com.enkiba.app.data.local.entity.HistoryEntity
import com.enkiba.app.data.local.entity.InventoryCountEntity
import com.enkiba.app.data.local.entity.InventorySessionEntity
import com.enkiba.app.data.local.entity.ProductCostEntity
import com.enkiba.app.data.local.entity.ProductEntity
import com.enkiba.app.data.local.entity.QuoteEntity
import com.enkiba.app.data.local.entity.QuoteItemEntity
import com.enkiba.app.data.local.entity.SaleEntity
import com.enkiba.app.data.local.entity.SaleItemCostEntity
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
 *
 * Faire évoluer le schéma (plus jamais de recréation : elle perdrait les ventes non envoyées) :
 * 1. modifier l'entité et incrémenter `version` ;
 * 2. compiler : le nouveau schéma est exporté dans app/schemas (à committer) ;
 * 3. écrire la Migration(ancienne, nouvelle) et l'ajouter à [MIGRATIONS] ;
 * 4. la tester avec MigrationTestHelper (room-testing) à partir des schémas exportés.
 */
@Database(
    entities = [
        ProductEntity::class, UserEntity::class, ClientEntity::class, SaleEntity::class,
        SaleItemEntity::class, HistoryEntity::class, StockMovementEntity::class,
        SaleReturnEntity::class, QuoteEntity::class, QuoteItemEntity::class,
        ProductCostEntity::class, SaleItemCostEntity::class,
        InventorySessionEntity::class, InventoryCountEntity::class,
    ],
    version = 13,
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
    abstract fun syncDao(): SyncRoomDao
    abstract fun costDao(): CostRoomDao
    abstract fun exportDao(): ExportRoomDao
    abstract fun inventoryDao(): InventoryRoomDao

    companion object {
        const val DATABASE_NAME = "stock.db"

        /** 11 → 12 : prix d'achat des produits et coût figé des lignes de vente (admins). */
        @JvmField
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `product_costs` (`product_id` TEXT NOT NULL, `shop_id` TEXT, " +
                        "`cost_price` REAL NOT NULL, `updated_at` TEXT, `sync_status` TEXT NOT NULL, PRIMARY KEY(`product_id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sale_item_costs` (`sale_item_id` TEXT NOT NULL, `shop_id` TEXT, " +
                        "`cost_price` REAL NOT NULL, `updated_at` TEXT, PRIMARY KEY(`sale_item_id`))"
                )
            }
        }

        /** 12 → 13 : inventaire (sessions et comptages). */
        @JvmField
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `inventory_sessions` (`id` TEXT NOT NULL, `shop_id` TEXT, `status` TEXT NOT NULL, " +
                        "`created_by` TEXT NOT NULL, `created_at` TEXT NOT NULL, `closed_at` TEXT, `updated_at` TEXT, " +
                        "`sync_status` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `inventory_counts` (`id` TEXT NOT NULL, `shop_id` TEXT, `session_id` TEXT NOT NULL, " +
                        "`product_id` TEXT NOT NULL, `counted` INTEGER NOT NULL, `expected` INTEGER NOT NULL, `counted_by` TEXT NOT NULL, " +
                        "`counted_at` TEXT NOT NULL, `updated_at` TEXT, `sync_status` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_inventory_counts_session_id_product_id` " +
                        "ON `inventory_counts` (`session_id`, `product_id`)"
                )
            }
        }

        /** Migrations du schéma, dans l'ordre. */
        @JvmField
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_11_12, MIGRATION_12_13)

        @Volatile
        private var instance: AppDatabase? = null

        @JvmStatic
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(appContext: Context): AppDatabase =
            Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
                .addMigrations(*MIGRATIONS)
                // Anciennes versions SQLiteOpenHelper : recréation (aucune donnée réelle)
                .fallbackToDestructiveMigrationFrom(true, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
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
