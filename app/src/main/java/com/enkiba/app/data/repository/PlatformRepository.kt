package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.api.RetrofitClient
import com.enkiba.app.models.ShopMemberOverview
import com.enkiba.app.models.ShopOverview
import com.enkiba.app.ui.shop.ShopForm
import com.enkiba.app.ui.users.UserForm
import com.enkiba.app.ui.users.serverErrorMessage
import com.enkiba.app.utils.NetworkUtils
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.awaitResponse
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Administration de la plateforme (super-admin) : toutes les boutiques, leurs indicateurs,
 * leurs membres, création, modification et suspension. Tout passe par le serveur, qui
 * vérifie lui-même le rôle (fonctions shop_overview, create-shop, policies).
 */
class PlatformRepository(context: Context) {

    private val appContext = context.applicationContext
    private val api get() = RetrofitClient.getSupabaseService(appContext)

    suspend fun shops(): TeamResult<List<ShopOverview>> = call {
        val res = api.shopOverview(JsonObject()).awaitResponse()
        val body = res.body()
        if (res.isSuccessful && body != null) TeamResult.Ok(body) else failure(res)
    }

    suspend fun members(shopId: String): TeamResult<List<ShopMemberOverview>> = call {
        val res = api.shopMembersOverview(JsonObject().apply { addProperty("p_shop", shopId) }).awaitResponse()
        val body = res.body()
        if (res.isSuccessful && body != null) TeamResult.Ok(body) else failure(res)
    }

    suspend fun update(shopId: String, form: ShopForm): TeamResult<Unit> = call {
        val fields = JsonObject().apply { form.toFields().forEach { (k, v) -> addProperty(k, v.toString()) } }
        patch(shopId, fields)
    }

    /** Suspend (date du téléphone, en UTC) ou réactive une boutique. */
    suspend fun setSuspended(shopId: String, suspended: Boolean): TeamResult<Unit> = call {
        val fields = JsonObject().apply {
            if (suspended) addProperty("suspended_at", utcNow()) else add("suspended_at", JsonNull.INSTANCE)
        }
        patch(shopId, fields)
    }

    suspend fun create(shop: ShopForm, admin: UserForm): TeamResult<Unit> = call {
        val body = JsonObject().apply {
            shop.toFields().forEach { (k, v) -> addProperty(k, v.toString()) }
            addProperty("admin_email", admin.email.trim())
            addProperty("admin_password", admin.password)
            addProperty("admin_username", admin.firstName.trim())
            addProperty("admin_lastname", admin.lastName.trim())
            addProperty("admin_telephone", admin.phone.trim())
        }
        val res = api.createShop(body).awaitResponse()
        if (res.isSuccessful) TeamResult.Ok(Unit) else failure(res)
    }

    private fun patch(shopId: String, fields: JsonObject): TeamResult<Unit> {
        val res = api.patchShop("eq.$shopId", fields).execute()
        // Aucune ligne renvoyée : la policy a refusé (pas super-admin)
        return if (res.isSuccessful && !res.body().isNullOrEmpty()) TeamResult.Ok(Unit) else failure(res)
    }

    private suspend fun <T> call(block: suspend () -> TeamResult<T>): TeamResult<T> = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isOnline(appContext)) return@withContext TeamResult.Failure("Connexion internet nécessaire")
        try {
            block()
        } catch (e: IOException) {
            TeamResult.Failure("Erreur réseau : réessayez")
        }
    }

    private fun failure(res: Response<*>): TeamResult.Failure =
        if (res.isSuccessful) TeamResult.Failure("Action réservée au super-administrateur")
        else TeamResult.Failure(serverErrorMessage(res.code(), res.errorBody()?.string()).replace("l'administrateur de la boutique", "au super-administrateur"))

    private fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}
