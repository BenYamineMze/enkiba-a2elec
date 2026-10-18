package com.enkiba.app.data.repository

import android.content.Context
import android.util.Log
import com.enkiba.app.api.RetrofitClient
import com.enkiba.app.data.local.LocalShopData
import com.enkiba.app.data.local.ShopStore
import com.enkiba.app.models.Membership
import com.enkiba.app.models.Shop
import com.enkiba.app.models.User
import com.enkiba.app.modelsDao.UserDao
import com.enkiba.app.utils.NetworkUtils
import com.enkiba.app.utils.SessionManager
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse

/** Résultat d'une tentative de connexion. */
sealed interface LoginResult {
    data object Success : LoginResult
    data class Failure(val message: String) : LoginResult
}

/**
 * Connexion Supabase : jetons → profil (actif ?) → appartenance à une boutique (rôle).
 * Le rôle vient toujours de shop_members, jamais des métadonnées du compte.
 * Aucun mot de passe n'est stocké ; la session (chiffrée) persiste hors ligne.
 */
class AuthRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val userDao = UserDao(appContext)

    fun isLoggedIn(): Boolean = session.isLoggedIn()

    suspend fun login(email: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isOnline(appContext)) {
            return@withContext LoginResult.Failure("Une connexion internet est nécessaire pour se connecter")
        }
        val api = RetrofitClient.getSupabaseService(appContext)
        try {
            val authRes = api.login(mapOf("email" to email, "password" to password)).awaitResponse()
            val auth = authRes.body()
            if (!authRes.isSuccessful || auth?.user == null) return@withContext fail("Identifiants incorrects")
            // Les jetons doivent exister avant les appels suivants (intercepteur d'authentification)
            session.saveTokens(auth.accessToken, auth.refreshToken, auth.computeExpiresAt())
            val userId = auth.user.id

            val profileRes = api.getProfile("eq.$userId", "*").awaitResponse()
            val profile = profileRes.body()?.firstOrNull()
            if (!profileRes.isSuccessful || profile == null) return@withContext fail("Profil introuvable")
            if (!profile.isActive) return@withContext fail("Ce compte est désactivé")

            val memberRes = api.getMemberships("eq.$userId", "shop_id,role").awaitResponse()
            val memberships = memberRes.body().orEmpty()
            if (!memberRes.isSuccessful || memberships.isEmpty()) return@withContext fail("Compte non rattaché à une boutique")

            // Boutique ouverte : celle des données déjà sur le téléphone si possible, sinon la première
            // visible. Une boutique invisible pour son membre est suspendue ou supprimée.
            val localShop = LocalShopData.owner(appContext)
            var membership: Membership? = null
            var shop: Shop? = null
            for (m in memberships.sortedByDescending { it.shopId == localShop }) {
                val res = api.getShop("eq.${m.shopId}", "id,name,address,phone,email,updated_at").awaitResponse()
                val found = res.body()?.firstOrNull()
                if (res.isSuccessful && found != null) { membership = m; shop = found; break }
            }
            if (membership == null || shop == null) {
                return@withContext fail("Cette boutique est suspendue. Contactez l'administrateur d'Enkiba.")
            }
            // Base locale : jamais deux boutiques mélangées sur le téléphone
            if (!LocalShopData.switchTo(appContext, membership.shopId)) {
                return@withContext fail("Ce téléphone contient des ventes non synchronisées d'une autre boutique. " +
                    "Connectez-vous avec un compte de cette boutique pour les envoyer.")
            }

            // Le local garde 'admin' / 'user' : seller → user
            val localRole = if (membership.role == "admin") "admin" else "user"
            session.saveSession(userId, profile.username, profile.lastname, profile.email ?: email, localRole, membership.shopId)
            // Coordonnées de la boutique pour les PDF (la synchro les tiendra à jour)
            ShopStore.save(appContext, shop)
            session.setShopCount(memberships.size)

            // Super-administrateur : la table ne renvoie que sa propre ligne (policy platform_admins_self)
            val superRes = runCatching { api.getPlatformAdmin("eq.$userId", "user_id").awaitResponse() }.getOrNull()
            session.setSuperAdmin(superRes?.isSuccessful == true && !superRes.body().isNullOrEmpty())

            // Cache local des noms (affichage des vendeurs) : aucun mot de passe
            userDao.insertOrUpdateUserFromRemote(User().apply {
                setId(userId)
                setAuthId(userId)
                setUsername(profile.username)
                setLastname(profile.lastname)
                setTelephone(profile.telephone ?: "")
                setEmail(profile.email)
                setRole(localRole)
                setActive(1)
                setUpdatedAt(profile.updatedAt)
            })
            LoginResult.Success
        } catch (e: IOException) {
            fail("Erreur réseau")
        } catch (e: Exception) {
            Log.e(TAG, "Connexion : ${e.message}", e)
            fail("Erreur inattendue")
        }
    }

    private fun fail(message: String): LoginResult {
        session.clearSession()
        Log.w(TAG, message)
        return LoginResult.Failure(message)
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
