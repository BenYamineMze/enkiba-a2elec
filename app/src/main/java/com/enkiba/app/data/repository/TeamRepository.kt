package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.api.RetrofitClient
import com.enkiba.app.models.ShopMember
import com.enkiba.app.ui.users.UserForm
import com.enkiba.app.ui.users.serverErrorMessage
import com.enkiba.app.utils.NetworkUtils
import com.enkiba.app.utils.SessionManager
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import retrofit2.awaitResponse
import java.io.IOException

/** Membre de la boutique tel que le serveur le connaît. */
data class TeamMember(
    val userId: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val isAdmin: Boolean,
    val active: Boolean,
) {
    val fullName: String get() = "$firstName $lastName".trim().ifEmpty { email }
}

sealed interface TeamResult<out T> {
    data class Ok<T>(val value: T) : TeamResult<T>
    data class Failure(val message: String) : TeamResult<Nothing>
}

/**
 * Équipe de la boutique (admin) : tout passe par le serveur (Edge Functions),
 * donc une connexion internet est nécessaire. Rien n'est gardé en local ici.
 */
class TeamRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val api get() = RetrofitClient.getSupabaseService(appContext)

    val myUserId: String? get() = session.userId

    suspend fun members(): TeamResult<List<TeamMember>> = call {
        val res = api.getShopMembers("eq.${session.shopId}", SELECT).awaitResponse()
        val body = res.body()
        if (!res.isSuccessful || body == null) failure(res)
        else TeamResult.Ok(
            body.mapNotNull(::toMember)
                // Admins d'abord, puis actifs, puis ordre alphabétique
                .sortedWith(compareBy<TeamMember>({ !it.isAdmin }, { !it.active }, { it.fullName.lowercase() }))
        )
    }

    suspend fun member(userId: String): TeamResult<TeamMember> = call {
        val res = api.getShopMember("eq.${session.shopId}", "eq.$userId", SELECT).awaitResponse()
        val m = res.body()?.firstOrNull()?.let(::toMember)
        if (!res.isSuccessful) failure(res) else if (m == null) TeamResult.Failure("Utilisateur introuvable") else TeamResult.Ok(m)
    }

    suspend fun setActive(userId: String, active: Boolean): TeamResult<Unit> = call {
        val body = JsonObject().apply {
            addProperty("shop_id", session.shopId)
            addProperty("user_id", userId)
            addProperty("active", active)
        }
        val res = api.setSellerStatus(body).awaitResponse()
        if (res.isSuccessful) TeamResult.Ok(Unit) else failure(res)
    }

    suspend fun create(form: UserForm): TeamResult<Unit> = call {
        val body = JsonObject().apply {
            addProperty("shop_id", session.shopId)
            addProperty("email", form.email.trim())
            addProperty("password", form.password)
            addProperty("username", form.firstName.trim())
            addProperty("lastname", form.lastName.trim())
            addProperty("telephone", form.phone.trim())
        }
        val res = api.createSeller(body).awaitResponse()
        if (res.isSuccessful) TeamResult.Ok(Unit) else failure(res)
    }

    suspend fun update(userId: String, form: UserForm): TeamResult<Unit> = call {
        val body = JsonObject().apply {
            addProperty("shop_id", session.shopId)
            addProperty("user_id", userId)
            addProperty("username", form.firstName.trim())
            addProperty("lastname", form.lastName.trim())
            addProperty("telephone", form.phone.trim())
            if (form.password.isNotEmpty()) addProperty("password", form.password)
        }
        val res = api.updateSeller(body).awaitResponse()
        if (res.isSuccessful) TeamResult.Ok(Unit) else failure(res)
    }

    /** Vérifie la connexion, exécute l'appel hors du thread principal, traduit les erreurs réseau. */
    private suspend fun <T> call(block: suspend () -> TeamResult<T>): TeamResult<T> = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isOnline(appContext)) return@withContext TeamResult.Failure(OFFLINE)
        try {
            block()
        } catch (e: IOException) {
            TeamResult.Failure("Erreur réseau : réessayez")
        }
    }

    private fun failure(res: Response<*>): TeamResult.Failure =
        TeamResult.Failure(serverErrorMessage(res.code(), res.errorBody()?.string()))

    private fun toMember(m: ShopMember): TeamMember? {
        val p = m.profile ?: return null
        return TeamMember(
            userId = m.userId,
            firstName = p.username.orEmpty(),
            lastName = p.lastname.orEmpty(),
            phone = p.telephone.orEmpty(),
            email = p.email.orEmpty(),
            isAdmin = m.role != "seller",
            active = p.isActive,
        )
    }

    companion object {
        const val OFFLINE = "Connexion internet nécessaire pour gérer l'équipe"
        private const val SELECT = "user_id,role,profiles(id,username,lastname,telephone,email,active)"
    }
}
