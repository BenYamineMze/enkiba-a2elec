package com.enkiba.app.data.repository

import android.content.Context
import com.enkiba.app.api.RetrofitClient
import com.enkiba.app.data.local.AppDatabase
import com.enkiba.app.data.local.entity.ChatMessageEntity
import com.enkiba.app.data.local.entity.SYNC_PENDING
import com.enkiba.app.data.local.entity.UserEntity
import com.enkiba.app.utils.NetworkUtils
import com.enkiba.app.utils.SessionManager
import com.enkiba.app.utils.SynchronizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.awaitResponse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Un message prêt à afficher. */
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val body: String,
    val createdAt: String,
    val mine: Boolean,
    /** Pas encore arrivé sur le serveur. */
    val pending: Boolean,
)

/** Discussion de la boutique : un seul canal pour tous les membres. */
class ChatRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(appContext)
    private val dao = db.chatDao()
    private val session = SessionManager(appContext)
    private val sync = SynchronizationManager(appContext)

    suspend fun messages(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val me = session.userId
        val names = db.userDao().getAll().associate { it.id to "${it.username} ${it.lastname}".trim() }
        dao.visible().map { m ->
            ChatMessage(
                id = m.id, senderId = m.senderId,
                senderName = if (m.senderId == me) "Moi" else names[m.senderId]?.ifBlank { null } ?: "Membre de l'équipe",
                body = m.body, createdAt = m.createdAt, mine = m.senderId == me, pending = m.syncStatus == SYNC_PENDING,
            )
        }
    }

    suspend fun send(text: String): Boolean = withContext(Dispatchers.IO) {
        val body = text.trim().take(MAX_LENGTH)
        if (body.isEmpty()) return@withContext false
        dao.upsert(
            ChatMessageEntity(UUID.randomUUID().toString(), session.shopId, session.userId, body, utcNow(), null, null, SYNC_PENDING),
        )
        true
    }

    /** Retire un message (le sien, ou n'importe lequel pour un admin). */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) { dao.markDeleted(id, utcNow()) }

    /** Envoi et réception du chat seul (écran ouvert), puis noms des expéditeurs inconnus. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        sync.syncChatNow()
        fetchMissingNames()
    }

    /** Messages non lus des autres membres (pastille de l'accueil). */
    suspend fun unread(): Int = withContext(Dispatchers.IO) { dao.unread(session.userId ?: "", lastRead()) }

    fun markAllRead(lastCreatedAt: String?) {
        if (lastCreatedAt == null) return
        prefs().edit().putString(key(), lastCreatedAt).apply()
    }

    private fun lastRead(): String? = prefs().getString(key(), null)
    private fun prefs() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key() = "last_read_${session.shopId}"

    private suspend fun fetchMissingNames() {
        if (!NetworkUtils.isOnline(appContext)) return
        val known = db.userDao().getAll().map { it.id }.toSet()
        val missing = dao.visible().map { it.senderId }.distinct().filterNot { it in known }
        if (missing.isEmpty()) return
        runCatching {
            RetrofitClient.getSupabaseService(appContext)
                .getProfiles("in.(${missing.joinToString(",")})", "id,username,lastname").awaitResponse().body()
        }.getOrNull()?.forEach { p ->
            db.userDao().insertIfMissing(
                UserEntity(p.id, p.username.orEmpty(), p.lastname.orEmpty(), "", null, "user", 1, p.updatedAt),
            )
        }
    }

    private fun utcNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    companion object {
        const val MAX_LENGTH = 2000
        private const val PREFS = "enkiba_chat"
    }
}
