package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.data.local.ShopStore
import com.enkiba.app.models.Client
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.models.SaleItemRow
import com.enkiba.app.modelsDao.ClientDao
import com.enkiba.app.modelsDao.SaleDao
import com.enkiba.app.modelsDao.UserDao
import com.enkiba.app.utils.PDFUtils
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Une vente et ce qu'il faut pour l'afficher. */
data class SaleDetail(
    val sale: SaleHeader,
    val items: List<SaleItemRow>,
    val client: Client?,
    val sellerName: String?,
)

/** Détail d'une vente, retours et facture. */
class SaleDetailRepository(context: Context) {

    private val appContext = context.applicationContext
    private val saleDao = SaleDao(appContext)
    private val clientDao = ClientDao(appContext)
    private val userDao = UserDao(appContext)
    private val session = SessionManager(appContext)

    suspend fun load(saleId: String): SaleDetail? = withContext(Dispatchers.IO) {
        val sale = saleDao.getSaleHeaderById(saleId) ?: return@withContext null
        val seller = userDao.getUserById(sale.sellerId)
        SaleDetail(
            sale = sale,
            items = saleDao.getSaleItems(saleId),
            client = sale.clientId?.let { clientDao.getClientById(it) },
            sellerName = seller?.let { "${it.username.orEmpty()} ${it.lastname.orEmpty()}".trim() } ?: sale.username,
        )
    }

    /** Retour d'articles (transaction : retour, stock local, historique). @return montant remboursé ou -1. */
    suspend fun returnItems(itemId: String, quantity: Int): Double = withContext(Dispatchers.IO) {
        saleDao.processReturn(itemId, quantity, session.userId)
    }

    /** Facture PDF (lignes nettes des retours). `activityContext` : une Activity. */
    suspend fun openInvoice(activityContext: Context, detail: SaleDetail): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val shop = ShopStore.current(appContext)
            PDFUtils.generateInvoicePdf(activityContext, detail.sale, detail.items, detail.client,
                shop.name, shop.address, shop.phone, shop.email) != null
        }.getOrDefault(false)
    }
}
