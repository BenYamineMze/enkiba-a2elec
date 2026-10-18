package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.models.ProductHistory
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.modelsDao.HistoryDao
import com.enkiba.app.modelsDao.QuoteDao
import com.enkiba.app.modelsDao.SaleDao
import com.enkiba.app.utils.PDFUtils
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Historique : ventes, devis, opérations sur les articles. Un vendeur ne voit que les siens. */
class HistoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val saleDao = SaleDao(appContext)
    private val quoteDao = QuoteDao(appContext)
    private val historyDao = HistoryDao(appContext)

    private val sellerFilter: String? get() = if (session.isAdmin) null else session.userId

    suspend fun sales(): List<SaleHeader> = withContext(Dispatchers.IO) {
        val seller = sellerFilter
        if (seller == null) saleDao.allSaleHeaders else saleDao.getSaleHeadersByUser(seller)
    }

    suspend fun quotes(): List<Quote> = withContext(Dispatchers.IO) { quoteDao.getQuotes(sellerFilter) }

    /** Opérations (admin) ; noms manquants remplacés par un libellé lisible. */
    suspend fun operations(): List<ProductHistory> = withContext(Dispatchers.IO) {
        historyDao.productHistory.onEach { h ->
            if (h.productName.isNullOrBlank()) h.productName = "Article supprimé"
            if (h.username.isNullOrBlank()) h.username = "Utilisateur inconnu"
        }
    }

    /** Exports PDF : `activityContext` doit être une Activity (ouverture du lecteur PDF). */
    suspend fun exportSales(activityContext: Context, sales: List<SaleHeader>): Boolean = withContext(Dispatchers.IO) {
        runCatching { PDFUtils.generateSalesReportPdf(activityContext, sales) }.getOrDefault(false)
    }

    suspend fun exportOperations(activityContext: Context, ops: List<ProductHistory>): Boolean = withContext(Dispatchers.IO) {
        runCatching { PDFUtils.generateProductHistoryPdf(activityContext, ops) }.getOrDefault(false)
    }
}
