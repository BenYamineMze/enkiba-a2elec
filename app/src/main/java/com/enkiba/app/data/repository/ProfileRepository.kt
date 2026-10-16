package com.enkiba.app.data.repository

import android.content.Context
import android.util.Log
import com.enkiba.app.api.RetrofitClient
import com.enkiba.app.modelsDao.UserDao
import com.enkiba.app.utils.NetworkUtils
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import java.io.IOException

/** Profil de l'utilisateur connecté. */
data class MyProfile(
    val firstName: String,
    val lastName: String,
    val phone: String,
    val email: String,
    val isAdmin: Boolean,
)

sealed interface SaveProfileResult {
    data object Saved : SaveProfileResult
    data class Failure(val message: String) : SaveProfileResult
}

/**
 * « Mon profil » : lecture locale (session + cache), modification sur le serveur
 * (connexion nécessaire), puis mise à jour de la session et du cache local.
 */
class ProfileRepository(context: Context) {

    private val appContext = context.applicationContext
    private val session = SessionManager(appContext)
    private val userDao = UserDao(appContext)

    suspend fun load(): MyProfile = withContext(Dispatchers.IO) {
        val cached = userDao.getUserById(session.userId)
        MyProfile(
            firstName = session.firstName,
            lastName = session.lastName,
            phone = cached?.telephone.orEmpty(),
            email = session.email.ifEmpty { cached?.email.orEmpty() },
            isAdmin = session.isAdmin,
        )
    }

    suspend fun save(firstName: String, lastName: String, phone: String): SaveProfileResult = withContext(Dispatchers.IO) {
        if (!NetworkUtils.isOnline(appContext)) {
            return@withContext SaveProfileResult.Failure("Une connexion internet est nécessaire pour modifier le profil")
        }
        val userId = session.userId ?: return@withContext SaveProfileResult.Failure("Session invalide")
        try {
            val fields = mapOf<String, Any>("username" to firstName, "lastname" to lastName, "telephone" to phone)
            val res = RetrofitClient.getSupabaseService(appContext).updateMyProfile("eq.$userId", fields).awaitResponse()
            val updated = res.body()?.firstOrNull()
            if (!res.isSuccessful || updated == null) {
                Log.w(TAG, "Profil non modifié (code ${res.code()}) ${res.errorBody()?.string()}")
                return@withContext SaveProfileResult.Failure("Le serveur a refusé la modification")
            }
            session.updateNames(updated.username, updated.lastname)
            userDao.getUserById(userId)?.let { u ->
                u.username = updated.username
                u.lastname = updated.lastname
                u.telephone = updated.telephone ?: ""
                u.updatedAt = updated.updatedAt
                userDao.insertOrUpdateUserFromRemote(u)
            }
            SaveProfileResult.Saved
        } catch (e: IOException) {
            SaveProfileResult.Failure("Erreur réseau")
        }
    }

    private companion object {
        const val TAG = "ProfileRepository"
    }
}
