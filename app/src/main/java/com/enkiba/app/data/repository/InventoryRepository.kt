package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.api.RetrofitClient
import com.enkiba.app.data.local.AppDatabase
import com.enkiba.app.data.local.ShopStore
import com.enkiba.app.data.local.entity.InventoryCountEntity
import com.enkiba.app.data.local.entity.InventorySessionEntity
import com.enkiba.app.data.local.entity.SYNC_PENDING
import com.enkiba.app.modelsDao.ProductDao
import com.enkiba.app.pdf.EnkibaPdf
import com.enkiba.app.ui.inventory.InventoryReview
import com.enkiba.app.ui.inventory.InventoryRules
import com.enkiba.app.utils.NetworkUtils
import com.enkiba.app.utils.SessionManager
import com.enkiba.app.utils.SynchronizationManager
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.coroutines.resume

/** État de l'inventaire vu par ce téléphone. */
data class InventorySnapshot(val session: InventorySessionEntity?, val review: InventoryReview)

/** Résultat d'une action qui demande le serveur (validation, annulation). */
sealed interface InventoryResult {
    /** [report] : rapport PDF de la validation (null pour une annulation ou si le PDF a échoué). */
    data class Done(val adjustments: Int, val report: File?) : InventoryResult
    data class Failure(val message: String) : InventoryResult
}

/**
 * Inventaire : un admin ouvre, tous les membres comptent (hors ligne possible),
 * un admin valide en ligne. Les écarts deviennent des mouvements de stock côté serveur.
 */
class InventoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val dao = db.inventoryDao()
    private val productDao = ProductDao(appContext)
    private val session = SessionManager(appContext)
    private val api get() = RetrofitClient.getSupabaseService(appContext)

    suspend fun load(): InventorySnapshot = withContext(Dispatchers.IO) {
        val open = dao.openSession()
        val counts = open?.let { dao.counts(it.id) }.orEmpty()
        // Les prix d'achat ne sont sur le téléphone que pour un admin
        val costs = if (session.isAdmin) db.costDao().all().associate { it.productId to it.costPrice } else emptyMap()
        InventorySnapshot(open, InventoryRules.review(productDao.allProducts, counts, costs))
    }

    /** Admin : ouvre un inventaire (envoyé à la prochaine synchro). */
    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (!session.isAdmin || dao.openSession() != null) return@withContext false
        dao.upsertSession(
            InventorySessionEntity(UUID.randomUUID().toString(), session.shopId, "open", session.userId, utcNow(), null, null, SYNC_PENDING),
        )
        true
    }

    /** Enregistre le comptage d'un article ; le stock attendu est celui du téléphone à cet instant. */
    suspend fun saveCount(sessionId: String, productId: String, counted: Int): Boolean = withContext(Dispatchers.IO) {
        val product = productDao.getProductById(productId) ?: return@withContext false
        val previous = dao.count(sessionId, productId)
        dao.upsertCount(
            InventoryCountEntity(
                id = previous?.id ?: UUID.randomUUID().toString(),
                shopId = session.shopId, sessionId = sessionId, productId = productId,
                counted = counted, expected = product.quantity,
                countedBy = session.userId, countedAt = utcNow(), updatedAt = null, syncStatus = SYNC_PENDING,
            ),
        )
        true
    }

    /**
     * Admin, en ligne : envoie tout, applique les écarts sur le serveur, puis récupère les
     * nouveaux stocks et produit le rapport PDF (écarts calculés avant la validation).
     */
    suspend fun validate(sessionId: String): InventoryResult {
        if (!NetworkUtils.isOnline(appContext)) return InventoryResult.Failure("Connexion internet nécessaire pour valider")
        syncAndWait()
        val before = load()
        val pending = withContext(Dispatchers.IO) { dao.pendingCountsIn(sessionId) + if (dao.session(sessionId)?.syncStatus == SYNC_PENDING) 1 else 0 }
        if (pending > 0) return InventoryResult.Failure("Des comptages n'ont pas encore pu être envoyés. Réessayez dans un instant.")
        val result = rpc { api.validateInventory(JsonObject().apply { addProperty("p_session", sessionId) }).awaitResponse() }
        if (result is InventoryResult.Failure) return result
        withContext(Dispatchers.IO) { dao.close(sessionId, "validated", utcNow()) }
        syncAndWait() // nouveaux stocks
        val report = withContext(Dispatchers.IO) {
            EnkibaPdf.inventoryReport(appContext, before.review, ShopStore.get(appContext, session.shopId), before.session?.createdAt)
        }
        return InventoryResult.Done((result as InventoryResult.Done).adjustments, report)
    }

    /** Admin, en ligne : abandonne l'inventaire sans toucher au stock. */
    suspend fun cancel(sessionId: String): InventoryResult {
        if (!NetworkUtils.isOnline(appContext)) return InventoryResult.Failure("Connexion internet nécessaire pour annuler")
        syncAndWait()
        val result = rpc { api.cancelInventory(JsonObject().apply { addProperty("p_session", sessionId) }).awaitResponse() }
        if (result is InventoryResult.Done) withContext(Dispatchers.IO) { dao.close(sessionId, "cancelled", utcNow()) }
        return result
    }

    private suspend fun rpc(call: suspend () -> retrofit2.Response<*>): InventoryResult = withContext(Dispatchers.IO) {
        try {
            val res = call()
            when {
                res.isSuccessful -> InventoryResult.Done((res.body() as? Number)?.toInt() ?: 0, null)
                res.errorBody()?.string().orEmpty().contains("Inventaire clos") ->
                    InventoryResult.Failure("Cet inventaire a déjà été clos sur un autre téléphone.")
                else -> InventoryResult.Failure("Le serveur a refusé (code ${res.code()})")
            }
        } catch (e: IOException) {
            InventoryResult.Failure("Erreur réseau : réessayez")
        }
    }

    private suspend fun syncAndWait() = suspendCancellableCoroutine { cont ->
        SynchronizationManager(appContext).startSynchronization { if (cont.isActive) cont.resume(Unit) }
    }

    private fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}
