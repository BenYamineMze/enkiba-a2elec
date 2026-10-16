package com.enkiba.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.enkiba.app.data.local.entity.ProductEntity
import com.enkiba.app.data.local.entity.QuoteEntity
import com.enkiba.app.data.local.entity.SYNC_PENDING
import com.enkiba.app.data.local.entity.SYNC_SYNCED
import com.enkiba.app.data.local.entity.SaleEntity
import com.enkiba.app.data.local.entity.SaleItemEntity
import com.enkiba.app.data.local.entity.SaleReturnEntity
import com.enkiba.app.data.local.entity.StockMovementEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Requêtes Room porteuses de règles métier : quantité vendue nette, montant encaissé,
 * écart de stock local ↔ serveur, conversion d'un devis.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 34 : le bac à sable Robolectric des SDK 35+ échoue avec le JDK de la JBR
// (FileDescriptor) ; Room ne dépend pas du niveau d'API.
@Config(sdk = [34])
class RoomDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun product(id: String, quantity: Int, sync: String = SYNC_SYNCED) = ProductEntity(
        id = id, shopId = "s", name = "Câble", category = "Élec", quantity = quantity, price = 100.0,
        description = null, qrCode = "QR-$id", initialStock = quantity, lowStockThreshold = 5,
        updatedAt = "2026-10-16T10:00:00+00:00", deletedAt = null, syncStatus = sync,
    )

    private fun sale(id: String, payment: String = "espece", deletedAt: String? = null, sync: String = SYNC_SYNCED) =
        SaleEntity(
            id = id, shopId = "s", sellerId = "u1", clientId = null, totalPrice = 300.0, paymentMethod = payment,
            hasNegotiation = false, saleDate = "2026-10-16T10:00:00+00:00", sourceQuoteId = null,
            updatedAt = null, deletedAt = deletedAt, syncStatus = sync,
        )

    private fun item(id: String, saleId: String, productId: String, qty: Int, sync: String = SYNC_SYNCED) =
        SaleItemEntity(
            id = id, shopId = "s", saleId = saleId, productId = productId, productName = "Câble", quantity = qty,
            listPrice = 100.0, unitPrice = 100.0, totalPrice = qty * 100.0, updatedAt = null, syncStatus = sync,
        )

    private fun ret(id: String, saleId: String, itemId: String, productId: String, qty: Int, sync: String = SYNC_SYNCED) =
        SaleReturnEntity(
            id = id, shopId = "s", saleId = saleId, saleItemId = itemId, productId = productId, quantity = qty,
            refundAmount = qty * 100.0, createdBy = "u1", createdAt = "2026-10-16T11:00:00+00:00",
            updatedAt = null, syncStatus = sync,
        )

    @Test
    fun quantiteVendueNetteDesRetoursEtDesVentesSupprimees() {
        db.productDao().insert(product("p1", 10))
        db.saleDao().insertSale(sale("s1"))
        db.saleDao().insertItem(item("i1", "s1", "p1", 3))
        db.saleDao().insertSale(sale("s2", deletedAt = "2026-10-16T12:00:00+00:00"))
        db.saleDao().insertItem(item("i2", "s2", "p1", 5))
        db.stockDao().insertReturn(ret("r1", "s1", "i1", "p1", 1))

        assertEquals(2, db.productDao().getById("p1")!!.soldQuantity) // 3 vendus - 1 retourné, vente s2 supprimée
    }

    @Test
    fun montantEncaisseMoinsRemboursements() {
        db.productDao().insert(product("p1", 10))
        db.saleDao().insertSale(sale("s1", "espece"))
        db.saleDao().insertItem(item("i1", "s1", "p1", 3))
        db.saleDao().insertSale(sale("s2", "virement"))
        db.stockDao().insertReturn(ret("r1", "s1", "i1", "p1", 1))

        assertEquals(500.0, db.saleDao().netTotal(null), 0.001)       // 300 + 300 - 100
        assertEquals(200.0, db.saleDao().netTotal("espece"), 0.001)
        assertEquals(100.0, db.saleDao().getById("s1")!!.refunded, 0.001)
        assertEquals(2, db.saleDao().getItemsWithReturned("s1").single().let { it.item.quantity - it.returned })
    }

    @Test
    fun ecartDeStockLocalNonEnvoye() {
        db.productDao().insert(product("p1", 10))
        db.stockDao().insertMovement(
            StockMovementEntity("m1", "s", "p1", 5, "restock", "u1", "2026-10-16T10:00:00+00:00", SYNC_PENDING)
        )
        db.saleDao().insertSale(sale("s1", sync = SYNC_PENDING))
        db.saleDao().insertItem(item("i1", "s1", "p1", 3, SYNC_PENDING))
        db.stockDao().insertReturn(ret("r1", "s1", "i1", "p1", 1, SYNC_PENDING))
        // Déjà envoyé : n'entre pas dans l'écart
        db.stockDao().insertMovement(
            StockMovementEntity("m0", "s", "p1", 10, "initial", "u1", "2026-10-16T09:00:00+00:00", SYNC_SYNCED)
        )

        assertEquals(5 - 3 + 1, db.productDao().pendingLocalDelta("p1"))
    }

    @Test
    fun conversionDevisUneSeuleFois() {
        val quote = QuoteEntity(
            id = "q1", shopId = "s", sellerId = "u1", clientId = null, status = "draft", totalPrice = 300.0,
            validUntil = "2026-11-15", notes = null, convertedSaleId = null, createdAt = "2026-10-16T10:00:00+00:00",
            updatedAt = null, deletedAt = null, syncStatus = SYNC_SYNCED,
        )
        db.quoteDao().insertQuote(quote)

        assertEquals(1, db.quoteDao().markConverted("q1", "s1", "2026-10-16T12:00:00+00:00"))
        assertEquals(0, db.quoteDao().markConverted("q1", "s2", "2026-10-16T12:05:00+00:00"))
        assertEquals("s1", db.quoteDao().getById("q1")!!.quote.convertedSaleId)
        assertEquals(0, db.quoteDao().updateStatus("q1", "draft", "2026-10-16T12:10:00+00:00"))
    }

    @Test
    fun produitSupprimeHorsDesListes() {
        db.productDao().insert(product("p1", 10))
        db.productDao().markDeleted("p1", "2026-10-16T12:00:00+00:00")

        assertEquals(0, db.productDao().getActive().size)
        assertNull(db.productDao().getByQr("QR-p1"))
        assertEquals(SYNC_PENDING, db.productDao().getSyncStatus("p1"))
    }
}
