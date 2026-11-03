package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.data.local.AppDatabase
import com.enkiba.app.models.Product
import com.enkiba.app.modelsDao.HistoryDao
import com.enkiba.app.modelsDao.ProductDao
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Liste des produits et suppression logique (admin). */
class ProductsRepository(context: Context) {

    private val appContext = context.applicationContext
    private val productDao = ProductDao(appContext)
    private val historyDao = HistoryDao(appContext)
    private val session = SessionManager(appContext)

    suspend fun load(): List<Product> = withContext(Dispatchers.IO) { productDao.allProducts }

    suspend fun stockValue(): Double = withContext(Dispatchers.IO) { productDao.totalStockValue }

    /** Produits sans prix d'achat (admin) : à compléter pour le calcul des marges. */
    suspend fun missingCostIds(): Set<String> =
        withContext(Dispatchers.IO) { AppDatabase.get(appContext).costDao().productIdsWithoutCost().toSet() }

    /** Suppression logique (deleted_at, envoyée à la synchro) + entrée d'historique. */
    suspend fun delete(product: Product): Boolean = withContext(Dispatchers.IO) {
        val ok = productDao.markProductForDeletion(product.id)
        if (ok) historyDao.insertHistory(product.id, session.userId, "Suppression", product.name, "Article supprimé")
        ok
    }
}
