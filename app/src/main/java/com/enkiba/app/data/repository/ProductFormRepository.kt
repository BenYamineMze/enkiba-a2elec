package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.models.Product
import com.enkiba.app.models.StockMovement
import com.enkiba.app.modelsDao.HistoryDao
import com.enkiba.app.modelsDao.ProductDao
import com.enkiba.app.ui.products.ProductFormRules
import com.enkiba.app.ui.products.ValidProduct
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Ajout et modification d'un produit (admin). Le stock passe par les mouvements :
 * création = mouvement 'initial', changement de quantité = 'restock' ou 'adjustment'.
 */
class ProductFormRepository(context: Context) {

    private val appContext = context.applicationContext
    private val productDao = ProductDao(appContext)
    private val historyDao = HistoryDao(appContext)
    private val session = SessionManager(appContext)

    suspend fun load(id: String): Product? = withContext(Dispatchers.IO) { productDao.getProductById(id) }

    /** Catégories proposées : celles par défaut puis celles déjà utilisées. */
    suspend fun categories(): List<String> = withContext(Dispatchers.IO) {
        (ProductFormRules.DEFAULT_CATEGORIES + productDao.allProducts.mapNotNull { it.category?.trim() })
            .filter { it.isNotEmpty() }.distinct()
    }

    /** @return le nouvel article, ou null en cas d'échec (rien n'est écrit). */
    suspend fun create(v: ValidProduct): Product? = withContext(Dispatchers.IO) {
        val p = Product(v.name, v.category, v.quantity, v.price, v.description, v.threshold, v.quantity)
        p.qr_code = ProductFormRules.newQrCode()
        productDao.createProduct(p, v.quantity, session.userId) ?: return@withContext null
        historyDao.insertHistory(p.id, session.userId, "Ajout", "-", "Stock initial : ${v.quantity}")
        p
    }

    suspend fun update(original: Product, v: ValidProduct): Boolean = withContext(Dispatchers.IO) {
        // Le stock est relu : une vente a pu avoir lieu depuis l'ouverture du formulaire
        val current = productDao.getProductById(original.id) ?: return@withContext false
        val delta = v.quantity - current.quantity
        val updated = Product(current).apply {
            name = v.name
            category = v.category
            price = v.price
            description = v.description
            lowStockThreshold = v.threshold
            initial_stock = ProductFormRules.newInitialStock(current.initial_stock, current.quantity, v.quantity)
        }
        if (!productDao.updateProduct(updated)) return@withContext false
        if (delta != 0) {
            val reason = if (delta > 0) StockMovement.REASON_RESTOCK else StockMovement.REASON_ADJUSTMENT
            if (!productDao.applyStockMovement(current.id, delta, reason, session.userId)) return@withContext false
        }
        historyDao.insertHistory(
            current.id, session.userId, "Modification",
            "${current.name} (stock : ${current.quantity}, prix : ${ProductFormRules.priceText(current.price)})",
            "${v.name} (stock : ${v.quantity}, prix : ${ProductFormRules.priceText(v.price)})",
        )
        true
    }
}
