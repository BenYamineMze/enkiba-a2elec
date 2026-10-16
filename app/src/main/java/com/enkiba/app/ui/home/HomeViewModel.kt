package com.enkiba.app.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.HomeData
import com.enkiba.app.data.repository.HomeRepository
import com.enkiba.app.modelsDao.UserDao
import com.enkiba.app.ui.components.SyncState
import com.enkiba.app.utils.NetworkUtils
import com.enkiba.app.utils.SessionManager
import com.enkiba.app.utils.SynchronizationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** État de l'accueil. `data` est null tant que le premier chargement n'est pas fini. */
data class HomeUiState(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val isAdmin: Boolean = false,
    val data: HomeData? = null,
    val syncing: Boolean = false,
    val online: Boolean = true,
) {
    val syncState: SyncState
        get() = when {
            syncing -> SyncState.SYNCING
            !online -> SyncState.OFFLINE
            (data?.pendingChanges ?: 0) > 0 -> SyncState.PENDING
            else -> SyncState.SYNCED
        }
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val session = SessionManager(application)
    private val repository = HomeRepository(application)
    private val syncManager = SynchronizationManager(application)

    private val _state = MutableStateFlow(
        HomeUiState(
            firstName = session.firstName,
            lastName = session.lastName,
            email = session.email,
            isAdmin = session.isAdmin,
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** Recharge les chiffres depuis la base locale. */
    fun refresh() {
        viewModelScope.launch {
            try {
                // Un vendeur ne voit que ses propres ventes et devis
                val data = repository.load(sellerId = if (session.isAdmin) null else session.userId)
                // Session ouverte avant l'enregistrement de l'e-mail : repli sur le cache local
                val email = _state.value.email.ifEmpty {
                    withContext(Dispatchers.IO) { UserDao(getApplication()).getUserById(session.userId)?.email }.orEmpty()
                }
                _state.update { it.copy(data = data, email = email, online = NetworkUtils.isOnline(getApplication())) }
            } catch (e: Exception) {
                Log.e(TAG, "Chargement de l'accueil : ${e.message}", e)
            }
        }
    }

    /** Lance une synchronisation (une seule à la fois), puis recharge l'accueil. */
    fun sync() {
        val online = NetworkUtils.isOnline(getApplication())
        _state.update { it.copy(online = online, syncing = online) }
        syncManager.startSynchronization {
            _state.update { it.copy(syncing = false) }
            refresh()
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
