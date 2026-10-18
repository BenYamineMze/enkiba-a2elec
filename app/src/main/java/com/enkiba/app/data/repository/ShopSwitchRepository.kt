package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.api.RetrofitClient
import com.enkiba.app.data.local.LocalShopData
import com.enkiba.app.data.local.ShopStore
import com.enkiba.app.utils.NetworkUtils
import com.enkiba.app.utils.SessionManager
import com.enkiba.app.utils.SynchronizationManager
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import java.io.IOException
import kotlin.coroutines.resume

/** Une boutique dont l'utilisateur est membre. [available] faux si elle est suspendue. */
data class MyShop(val shopId: String, val name: String, val role: String, val available: Boolean, val current: Boolean) {
    val isAdmin: Boolean get() = role == "admin"
}

sealed interface SwitchResult {
    data object Done : SwitchResult
    data class Failure(val message: String) : SwitchResult
}

/**
 * Changement de boutique : envoie d'abord les changements en attente, refuse s'il en reste,
 * vide la base locale, bascule la session puis télécharge la nouvelle boutique.
 */
class ShopSwitchRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val api get() = RetrofitClient.getSupabaseService(appContext)

    suspend fun myShops(): TeamResult<List<MyShop>> = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isOnline(appContext)) return@withContext TeamResult.Failure("Connexion internet nécessaire")
        try {
            val res = api.getMembershipsDetailed("eq.${session.userId}", "shop_id,role,shops(name)").awaitResponse()
            val rows = res.body() ?: return@withContext TeamResult.Failure("Liste des boutiques indisponible")
            val shops = rows.map { o ->
                val shopObj = o.get("shops")?.takeIf { it.isJsonObject }?.asJsonObject
                val id = o.get("shop_id").asString
                MyShop(
                    shopId = id,
                    name = shopObj?.get("name")?.asString ?: "Boutique suspendue",
                    role = o.get("role")?.asString ?: "seller",
                    available = shopObj != null,
                    current = id == session.shopId,
                )
            }.sortedWith(compareBy({ !it.current }, { !it.available }, { it.name.lowercase() }))
            session.setShopCount(shops.size)
            TeamResult.Ok(shops)
        } catch (e: IOException) {
            TeamResult.Failure("Erreur réseau : réessayez")
        }
    }

    suspend fun switchTo(target: MyShop): SwitchResult {
        if (!NetworkUtils.isOnline(appContext)) return SwitchResult.Failure("Connexion internet nécessaire pour changer de boutique")
        // 1. Envoyer ce qui reste (ventes faites hors ligne, etc.)
        if (withContext(Dispatchers.IO) { LocalShopData.pendingChanges(appContext) } > 0) syncAndWait()
        return withContext(Dispatchers.IO) {
            try {
                // 2. La boutique visée doit être accessible
                val shop = api.getShop("eq.${target.shopId}", "id,name,address,phone,email,updated_at").awaitResponse().body()?.firstOrNull()
                    ?: return@withContext SwitchResult.Failure("Cette boutique est suspendue ou indisponible")
                // 3. Base locale : vidée seulement si tout a été envoyé
                if (!LocalShopData.switchTo(appContext, target.shopId)) {
                    return@withContext SwitchResult.Failure(
                        "Des changements n'ont pas encore pu être envoyés. Réessayez quand la synchronisation aura réussi."
                    )
                }
                session.switchShop(target.shopId, if (target.isAdmin) "admin" else "user")
                ShopStore.save(appContext, shop)
                SwitchResult.Done
            } catch (e: IOException) {
                SwitchResult.Failure("Erreur réseau : réessayez")
            }
        }.also { if (it == SwitchResult.Done) syncAndWait() } // 4. Télécharger la nouvelle boutique
    }

    /** Super-admin : se rattacher comme admin à une boutique (pour pouvoir y basculer). */
    suspend fun join(shopId: String): TeamResult<Unit> = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isOnline(appContext)) return@withContext TeamResult.Failure("Connexion internet nécessaire")
        try {
            val res = api.joinShopAsAdmin(JsonObject().apply { addProperty("p_shop", shopId) }).awaitResponse()
            if (res.isSuccessful) {
                session.setShopCount(session.shopCount + 1)
                TeamResult.Ok(Unit)
            } else TeamResult.Failure("Rattachement refusé (code ${res.code()})")
        } catch (e: IOException) {
            TeamResult.Failure("Erreur réseau : réessayez")
        }
    }

    private suspend fun syncAndWait() = suspendCancellableCoroutine { cont ->
        SynchronizationManager(appContext).startSynchronization { if (cont.isActive) cont.resume(Unit) }
    }
}
