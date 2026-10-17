package com.enkiba.app.data.repository

import android.content.Context
import android.util.Log
import com.enkiba.app.api.RetrofitClient
import com.enkiba.app.data.local.ShopDetails
import com.enkiba.app.data.local.ShopStore
import com.enkiba.app.ui.shop.ShopForm
import com.enkiba.app.utils.NetworkUtils
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import java.io.IOException

sealed interface SaveShopResult {
    data object Saved : SaveShopResult
    data class Failure(val message: String) : SaveShopResult
}

/**
 * « Ma boutique » : lecture depuis le cache local (immédiate, hors ligne), rafraîchie
 * depuis le serveur quand c'est possible ; modification sur le serveur (admin, en ligne).
 */
class ShopRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val api get() = RetrofitClient.getSupabaseService(appContext)

    fun cached(): ShopDetails = ShopStore.get(appContext, session.shopId)

    /** Version du serveur, enregistrée dans le cache ; null hors ligne ou en cas d'erreur. */
    suspend fun refresh(): ShopDetails? = withContext(Dispatchers.IO) {
        val shopId = session.shopId ?: return@withContext null
        if (!NetworkUtils.isOnline(appContext)) return@withContext null
        try {
            val shop = api.getShop("eq.$shopId", SELECT).awaitResponse().body()?.firstOrNull() ?: return@withContext null
            ShopStore.save(appContext, shop)
            ShopStore.get(appContext, shopId)
        } catch (e: IOException) {
            null
        }
    }

    suspend fun save(form: ShopForm): SaveShopResult = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isOnline(appContext)) {
            return@withContext SaveShopResult.Failure("Une connexion internet est nécessaire pour enregistrer")
        }
        val shopId = session.shopId ?: return@withContext SaveShopResult.Failure("Session invalide")
        try {
            val res = api.updateShop("eq.$shopId", form.toFields()).awaitResponse()
            val updated = res.body()?.firstOrNull()
            if (!res.isSuccessful || updated == null) {
                // Aucune ligne renvoyée : la policy a refusé (pas admin de cette boutique)
                Log.w(TAG, "Boutique non modifiée (code ${res.code()}) ${res.errorBody()?.string()}")
                return@withContext SaveShopResult.Failure("Le serveur a refusé la modification")
            }
            ShopStore.save(appContext, updated)
            SaveShopResult.Saved
        } catch (e: IOException) {
            SaveShopResult.Failure("Erreur réseau")
        }
    }

    private companion object {
        const val TAG = "ShopRepository"
        const val SELECT = "id,name,address,phone,email,updated_at"
    }
}
