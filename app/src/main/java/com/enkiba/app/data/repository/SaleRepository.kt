package com.enkiba.app.data.repository

import android.content.Context
import android.util.Log
import com.enkiba.app.data.local.ShopStore
import com.enkiba.app.models.Client
import com.enkiba.app.models.Product
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.models.SaleItemRow
import com.enkiba.app.modelsDao.ClientDao
import com.enkiba.app.modelsDao.ProductDao
import com.enkiba.app.modelsDao.QuoteDao
import com.enkiba.app.modelsDao.SaleDao
import com.enkiba.app.ui.sale.CartLine
import com.enkiba.app.utils.PDFUtils
import com.enkiba.app.utils.SessionManager
import com.enkiba.app.utils.WhatsAppShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Devis enregistré, avec ce qu'il faut pour son PDF. */
data class SavedQuote(val quote: Quote, val rows: List<SaleItemRow>, val client: Client)

/** Informations client saisies à la caisse (facture ou devis). */
data class ClientInput(
    val lastname: String = "",
    val firstname: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
) {
    val isEmpty: Boolean get() = lastname.isBlank()

    fun toClient(): Client = Client().apply {
        lastname = this@ClientInput.lastname.trim()
        firstname = this@ClientInput.firstname.trim()
        address = this@ClientInput.address.trim()
        phone = this@ClientInput.phone.trim()
        email = this@ClientInput.email.trim()
    }
}

/**
 * Caisse : produits à vendre, enregistrement des ventes et des devis.
 * Toutes les écritures passent par les DAO existants (transactions Room).
 */
class SaleRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val productDao = ProductDao(appContext)
    private val clientDao = ClientDao(appContext)
    private val saleDao = SaleDao(appContext)
    private val quoteDao = QuoteDao(appContext)

    suspend fun loadProducts(): List<Product> = withContext(Dispatchers.IO) { productDao.allProductsList }

    suspend fun findByQr(code: String): Product? = withContext(Dispatchers.IO) { productDao.getProductByQr(code) }

    suspend fun findById(id: String): Product? = withContext(Dispatchers.IO) { productDao.getProductById(id) }

    /**
     * Enregistre la vente (client facultatif, enregistré avant la vente).
     * @return l'en-tête de la vente enregistrée, ou null en cas d'échec.
     */
    suspend fun recordSale(lines: List<CartLine>, paymentMethod: String, client: ClientInput?): SaleHeader? =
        withContext(Dispatchers.IO) {
            val savedClient = client?.takeUnless { it.isEmpty }?.toClient()?.takeIf { clientDao.saveClientLocally(it) }
            val header = SaleHeader().apply {
                sellerId = session.userId // le vendeur est toujours l'utilisateur connecté
                clientId = savedClient?.id
                totalPrice = lines.sumOf { it.total }
                this.paymentMethod = paymentMethod
            }
            // Le drapeau « prix négocié » est calculé par recordSale à partir du prix catalogue
            val saleId = saleDao.recordSale(header, lines.map { it.toRow() }) ?: return@withContext null
            saleDao.getSaleHeaderById(saleId)
        }

    /** Client d'une vente (pour la facture). */
    suspend fun clientOf(sale: SaleHeader): Client? = withContext(Dispatchers.IO) {
        sale.clientId?.let { clientDao.getClientById(it) }
    }

    /**
     * Enregistre un devis (client obligatoire).
     * @return le devis et ses lignes (pour le PDF), ou null en cas d'échec.
     */
    suspend fun createQuote(lines: List<CartLine>, client: ClientInput): SavedQuote? = withContext(Dispatchers.IO) {
        val c = client.toClient()
        if (!clientDao.saveClientLocally(c)) return@withContext null
        val quote = Quote().apply {
            clientId = c.id
            sellerId = session.userId
        }
        val rows = lines.map { it.toRow() }
        if (quoteDao.createQuote(quote, rows) == null) return@withContext null
        SavedQuote(quote, rows, c)
    }

    /** Facture d'une vente à partager (PDF non ouvert + message) ; null si échec. */
    suspend fun invoiceToShare(activityContext: Context, sale: SaleHeader, client: Client?): SharedPdf? =
        withContext(Dispatchers.IO) {
            runCatching {
                val shop = ShopStore.current(appContext)
                PDFUtils.buildInvoicePdf(activityContext, sale, saleDao.getSaleItems(sale.id), client,
                    shop.name, shop.address, shop.phone, shop.email)?.let {
                    SharedPdf(it, client?.phone, WhatsAppShare.invoiceMessage(client?.firstname, sale.shortRef, sale.netTotal, shop.name))
                }
            }.onFailure { Log.e(TAG, "Partage facture : ${it.message}", it) }.getOrNull()
        }

    /** Devis à partager (PDF non ouvert + message) ; null si échec. */
    suspend fun quoteToShare(activityContext: Context, saved: SavedQuote): SharedPdf? = withContext(Dispatchers.IO) {
        runCatching {
            val shop = ShopStore.current(appContext)
            PDFUtils.buildQuotePdf(activityContext, saved.quote, saved.rows, saved.client,
                shop.name, shop.address, shop.phone, shop.email)?.let {
                SharedPdf(it, saved.client.phone,
                    WhatsAppShare.quoteMessage(saved.client.firstname, saved.quote.shortRef, saved.quote.totalPrice, shop.name, saved.quote.validUntil))
            }
        }.onFailure { Log.e(TAG, "Partage devis : ${it.message}", it) }.getOrNull()
    }

    /**
     * Génère et ouvre un PDF. `activityContext` doit être une Activity :
     * l'ouverture du lecteur PDF est refusée depuis le contexte de l'application.
     */
    suspend fun openInvoicePdf(activityContext: Context, sale: SaleHeader, client: Client?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val shop = ShopStore.current(appContext)
                PDFUtils.generateInvoicePdf(activityContext, sale, saleDao.getSaleItems(sale.id), client,
                    shop.name, shop.address, shop.phone, shop.email) != null
            }.onFailure { Log.e(TAG, "Facture : ${it.message}", it) }.getOrDefault(false)
        }

    suspend fun openQuotePdf(activityContext: Context, saved: SavedQuote): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val shop = ShopStore.current(appContext)
            PDFUtils.generateQuotePdf(activityContext, saved.quote, saved.rows, saved.client,
                shop.name, shop.address, shop.phone, shop.email) != null
        }.onFailure { Log.e(TAG, "Devis PDF : ${it.message}", it) }.getOrDefault(false)
    }

    private companion object {
        const val TAG = "SaleRepository"
    }
}
