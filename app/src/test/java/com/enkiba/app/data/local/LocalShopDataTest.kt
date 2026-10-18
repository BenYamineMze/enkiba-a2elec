package com.enkiba.app.data.local

import androidx.test.core.app.ApplicationProvider
import com.enkiba.app.data.local.entity.ProductEntity
import com.enkiba.app.data.local.entity.SYNC_PENDING
import com.enkiba.app.data.local.entity.SYNC_SYNCED
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bascule de la base locale entre boutiques : jamais de mélange, jamais de perte. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalShopDataTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = AppDatabase.get(context)

    @After fun tearDown() = onIo { db.clearAllTables() }

    /** Room refuse le thread principal : le corps du test tourne dans un autre thread. */
    private fun onIo(block: () -> Unit) {
        var error: Throwable? = null
        Thread { try { block() } catch (t: Throwable) { error = t } }.apply { start(); join() }
        error?.let { throw it }
    }

    private fun product(shop: String, status: String) = ProductEntity(
        id = "p-$shop-$status", shopId = shop, name = "Câble", category = "", quantity = 0, price = 1.0,
        description = null, qrCode = null, initialStock = 0, lowStockThreshold = 5, updatedAt = null, deletedAt = null, syncStatus = status,
    )

    @Test
    fun memeBoutiqueRienNEstEfface() = onIo {
        db.productDao().upsert(product("A", SYNC_SYNCED))
        assertTrue(LocalShopData.switchTo(context, "A"))
        assertEquals("A", LocalShopData.owner(context))
        assertEquals(1, db.productDao().countActive())
    }

    @Test
    fun changementsNonEnvoyesBloquentLaBascule() = onIo {
        db.productDao().upsert(product("A", SYNC_PENDING))
        assertTrue(LocalShopData.switchTo(context, "A"))
        assertFalse(LocalShopData.switchTo(context, "B"))
        assertEquals("A", LocalShopData.owner(context))
        assertEquals(1, db.productDao().countActive())
    }

    @Test
    fun basculeApresEnvoiVideLaBase() = onIo {
        db.productDao().upsert(product("A", SYNC_SYNCED))
        assertTrue(LocalShopData.switchTo(context, "A"))
        assertTrue(LocalShopData.switchTo(context, "B"))
        assertEquals("B", LocalShopData.owner(context))
        assertEquals(0, db.productDao().countActive())
    }
}
