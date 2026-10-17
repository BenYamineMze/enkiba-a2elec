package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.data.local.ShopStore
import com.enkiba.app.models.Client
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.models.SaleItemRow
import com.enkiba.app.modelsDao.ClientDao
import com.enkiba.app.modelsDao.ProductDao
import com.enkiba.app.modelsDao.QuoteDao
import com.enkiba.app.modelsDao.SaleDao
import com.enkiba.app.modelsDao.UserDao
import com.enkiba.app.utils.PDFUtils
import com.enkiba.app.utils.SessionManager
import com.enkiba.app.utils.WhatsAppShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class QuoteDetail(
    val quote: Quote,
    val items: List<SaleItemRow>,
    val client: Client?,
    val sellerName: String?,
    /** Auteur du devis ou admin : seuls eux peuvent le modifier (règle du serveur). */
    val canEdit: Boolean,
)

/** Détail d'un devis : statut, suppression, PDF, conversion en vente. */
class QuoteDetailRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val quoteDao = QuoteDao(appContext)
    private val saleDao = SaleDao(appContext)
    private val productDao = ProductDao(appContext)
    private val clientDao = ClientDao(appContext)
    private val userDao = UserDao(appContext)

    suspend fun load(quoteId: String): QuoteDetail? = withContext(Dispatchers.IO) {
        val q = quoteDao.getQuoteById(quoteId)?.takeIf { it.deletedAt == null } ?: return@withContext null
        val seller = userDao.getUserById(q.sellerId)
        QuoteDetail(
            quote = q,
            items = quoteDao.getQuoteItems(quoteId),
            client = q.clientId?.let { clientDao.getClientById(it) },
            sellerName = seller?.let { "${it.username.orEmpty()} ${it.lastname.orEmpty()}".trim() },
            canEdit = session.isAdmin || q.sellerId == session.userId,
        )
    }

    suspend fun setStatus(quoteId: String, status: String): Boolean = withContext(Dispatchers.IO) { quoteDao.updateStatus(quoteId, status) }

    suspend fun delete(quoteId: String): Boolean = withContext(Dispatchers.IO) { quoteDao.markDeleted(quoteId) }

    /** Avertissements avant conversion : devis expiré, produit supprimé, stock insuffisant. */
    suspend fun conversionWarnings(detail: QuoteDetail): List<String> = withContext(Dispatchers.IO) {
        buildList {
            if (detail.quote.isExpired) add("Ce devis a expiré le ${detail.quote.validUntil}.")
            detail.items.forEach { r ->
                val p = productDao.getProductById(r.productId)
                when {
                    p == null || p.deletedAt != null -> add("${r.productName} n'existe plus dans le catalogue.")
                    p.quantity < r.quantity -> add("${r.productName} : ${p.quantity} en stock pour ${r.quantity} demandés.")
                }
            }
        }
    }

    /**
     * Vente + lignes + stock + historique + devis 'converted' en une transaction.
     * Prix du devis conservés ; vente au nom de l'utilisateur connecté.
     * @return l'id de la vente, ou null (devis déjà converti, erreur).
     */
    suspend fun convert(detail: QuoteDetail, paymentMethod: String): String? = withContext(Dispatchers.IO) {
        val sale = SaleHeader().apply {
            sellerId = session.userId
            clientId = detail.quote.clientId
            totalPrice = detail.items.sumOf { it.total }
            this.paymentMethod = paymentMethod
        }
        saleDao.recordSale(sale, detail.items, detail.quote.id)
    }

    /** Devis PDF à partager (non ouvert) et son message ; null si la génération échoue. */
    suspend fun quoteToShare(activityContext: Context, detail: QuoteDetail): SharedPdf? = withContext(Dispatchers.IO) {
        runCatching {
            val shop = ShopStore.current(appContext)
            PDFUtils.buildQuotePdf(activityContext, detail.quote, detail.items, detail.client,
                shop.name, shop.address, shop.phone, shop.email)?.let { file ->
                SharedPdf(
                    file = file,
                    phone = detail.client?.phone,
                    message = WhatsAppShare.quoteMessage(detail.client?.firstname, detail.quote.shortRef,
                        detail.quote.totalPrice, shop.name, detail.quote.validUntil),
                )
            }
        }.getOrNull()
    }

    suspend fun openPdf(activityContext: Context, detail: QuoteDetail): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val shop = ShopStore.current(appContext)
            PDFUtils.generateQuotePdf(activityContext, detail.quote, detail.items, detail.client,
                shop.name, shop.address, shop.phone, shop.email) != null
        }.getOrDefault(false)
    }
}
