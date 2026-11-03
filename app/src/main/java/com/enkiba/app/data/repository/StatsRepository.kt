package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.data.local.AppDatabase
import com.enkiba.app.models.Product
import com.enkiba.app.modelsDao.ProductDao
import com.enkiba.app.ui.stats.MonthKey
import com.enkiba.app.ui.stats.MarginStats
import com.enkiba.app.ui.stats.MonthStats
import com.enkiba.app.ui.stats.StockValue
import com.enkiba.app.ui.stats.marginStats
import com.enkiba.app.ui.stats.monthRangeUtc
import com.enkiba.app.ui.stats.monthStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Statistiques de la boutique (admin) : encaissé par mois et par jour, état du stock. */
class StatsRepository(context: Context) {

    private val db = AppDatabase.get(context)
    private val productDao = ProductDao(context.applicationContext)

    suspend fun month(month: MonthKey): MonthStats = withContext(Dispatchers.IO) {
        val (start, end) = monthRangeUtc(month)
        monthStats(month, db.saleDao().netRowsBetween(start, end))
    }

    /** Marge brute du mois (prix d'achat figé à la vente). */
    suspend fun margin(month: MonthKey): MarginStats = withContext(Dispatchers.IO) {
        val (start, end) = monthRangeUtc(month)
        marginStats(db.costDao().marginRowsBetween(start, end))
    }

    suspend fun stockValue(): StockValue = withContext(Dispatchers.IO) {
        StockValue(
            atSalePrice = productDao.totalStockValue,
            atCost = db.costDao().stockValueAtCost(),
            missingCost = db.costDao().productIdsWithoutCost().size,
        )
    }

    suspend fun products(): List<Product> = withContext(Dispatchers.IO) { productDao.allProducts }
}
