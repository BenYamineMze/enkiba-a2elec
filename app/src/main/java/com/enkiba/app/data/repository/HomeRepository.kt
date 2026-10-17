package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.data.local.AppDatabase
import com.enkiba.app.data.local.Mappers
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.ui.components.localDateString
import com.enkiba.app.ui.components.localDayRangeUtc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Chiffres de l'accueil. Un vendeur ne voit que ses ventes et ses devis. */
data class HomeData(
    val todayNet: Double,
    val todayCash: Double,
    val todayTransfer: Double,
    val yesterdayNet: Double,
    val todaySalesCount: Int,
    val lowStockCount: Int,
    val openQuotesCount: Int,
    val productCount: Int,
    val stockValue: Double,
    val recentSales: List<SaleHeader>,
    val pendingChanges: Int,
) {
    /** Part des espèces dans l'encaissé du jour (0 à 1), 0 sans vente. */
    val cashShare: Float
        get() = if (todayNet <= 0.0) 0f else (todayCash / todayNet).toFloat().coerceIn(0f, 1f)

    /** Évolution en % par rapport à hier, null si hier = 0 (pas de comparaison possible). */
    val changeVsYesterdayPercent: Int?
        get() = if (yesterdayNet <= 0.0) null else Math.round((todayNet - yesterdayNet) / yesterdayNet * 100).toInt()
}

/** Lecture des chiffres de l'accueil (base Room, hors du thread principal). */
class HomeRepository(context: Context) {

    private val db = AppDatabase.get(context)

    suspend fun load(sellerId: String?): HomeData = withContext(Dispatchers.IO) {
        val (todayStart, todayEnd) = localDayRangeUtc()
        val (yStart, yEnd) = localDayRangeUtc(offsetDays = -1)
        val sales = db.saleDao()
        HomeData(
            todayNet = sales.netTotalBetween(todayStart, todayEnd, sellerId, null),
            todayCash = sales.netTotalBetween(todayStart, todayEnd, sellerId, "espece"),
            todayTransfer = sales.netTotalBetween(todayStart, todayEnd, sellerId, "virement"),
            yesterdayNet = sales.netTotalBetween(yStart, yEnd, sellerId, null),
            todaySalesCount = sales.countBetween(todayStart, todayEnd, sellerId),
            lowStockCount = db.productDao().lowStockCount(),
            openQuotesCount = db.quoteDao().countOpen(localDateString(), sellerId),
            productCount = db.productDao().countActive(),
            stockValue = db.productDao().totalStockValue(),
            recentSales = sales.getRecent(sellerId, RECENT_SALES).map { Mappers.toModel(it) },
            pendingChanges = db.syncDao().countPending(),
        )
    }

    private companion object {
        const val RECENT_SALES = 5
    }
}
