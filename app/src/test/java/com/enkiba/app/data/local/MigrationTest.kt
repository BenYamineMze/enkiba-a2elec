package com.enkiba.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Migrations Room : les données existantes (ventes en attente) doivent survivre. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test
    fun migration11Vers12GardeLesVentesEtAjouteLesCouts() {
        helper.createDatabase(DB, 11).use { db ->
            db.execSQL(
                "INSERT INTO sales (id, shop_id, seller_id, client_id, total_price, payment_method, has_negotiation, " +
                    "sale_date, source_quote_id, updated_at, deleted_at, sync_status) " +
                    "VALUES ('s1', 'shop', 'u1', NULL, 500, 'espece', 0, '2026-11-01T10:00', NULL, NULL, NULL, 'pending')"
            )
        }
        // Valide aussi le schéma obtenu contre 12.json
        helper.runMigrationsAndValidate(DB, 12, true, AppDatabase.MIGRATION_11_12).use { db ->
            db.query("SELECT COUNT(*) FROM sales WHERE sync_status = 'pending'").use { c ->
                c.moveToFirst()
                assertEquals(1, c.getInt(0))
            }
            db.execSQL("INSERT INTO product_costs VALUES ('p1', 'shop', 60, NULL, 'pending')")
            db.execSQL("INSERT INTO sale_item_costs VALUES ('i1', 'shop', 60, NULL)")
        }
    }

    @Test
    fun migration12Vers13AjouteLInventaire() {
        helper.createDatabase(DB, 12).close()
        helper.runMigrationsAndValidate(DB, 13, true, AppDatabase.MIGRATION_12_13).use { db ->
            db.execSQL("INSERT INTO inventory_sessions VALUES ('i1', 'shop', 'open', 'u1', '2026-11-03T10:00', NULL, NULL, 'pending')")
            db.execSQL("INSERT INTO inventory_counts VALUES ('c1', 'shop', 'i1', 'p1', 5, 7, 'u1', '2026-11-03T10:05', NULL, 'pending')")
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
