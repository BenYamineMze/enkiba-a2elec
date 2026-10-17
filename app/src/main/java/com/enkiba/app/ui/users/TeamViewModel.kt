package com.enkiba.app.ui.users

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.TeamMember
import com.enkiba.app.data.repository.TeamRepository
import com.enkiba.app.data.repository.TeamResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TeamUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val members: List<TeamMember> = emptyList(),
    /** Liste indisponible (hors ligne, erreur) : affichée à la place de la liste. */
    val error: String? = null,
    /** Vendeur à désactiver, en attente de confirmation. */
    val confirmDeactivate: TeamMember? = null,
    /** Vendeur dont le statut est en cours de modification. */
    val busyUserId: String? = null,
    val message: String? = null,
) {
    val activeSellers: Int get() = members.count { !it.isAdmin && it.active }
}

class TeamViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TeamRepository(application)
    private val _state = MutableStateFlow(TeamUiState())
    val state: StateFlow<TeamUiState> = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        if (refresh) _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            when (val r = repository.members()) {
                is TeamResult.Ok -> _state.update { it.copy(loading = false, refreshing = false, members = r.value, error = null) }
                // Liste déjà affichée : on la garde et on prévient par un message
                is TeamResult.Failure -> _state.update {
                    if (it.members.isEmpty()) it.copy(loading = false, refreshing = false, error = r.message)
                    else it.copy(loading = false, refreshing = false, message = r.message)
                }
            }
        }
    }

    /** Activation directe ; la désactivation demande une confirmation. */
    fun onToggle(member: TeamMember, active: Boolean) {
        if (member.isAdmin || _state.value.busyUserId != null) return
        if (!active) _state.update { it.copy(confirmDeactivate = member) } else setActive(member, true)
    }

    fun confirmDeactivate() {
        val m = _state.value.confirmDeactivate ?: return
        _state.update { it.copy(confirmDeactivate = null) }
        setActive(m, false)
    }

    fun cancelDeactivate() = _state.update { it.copy(confirmDeactivate = null) }

    private fun setActive(member: TeamMember, active: Boolean) {
        _state.update { it.copy(busyUserId = member.userId) }
        viewModelScope.launch {
            val r = repository.setActive(member.userId, active)
            _state.update {
                it.copy(
                    busyUserId = null,
                    message = when (r) {
                        is TeamResult.Ok -> if (active) "${member.fullName} peut de nouveau se connecter" else "${member.fullName} est désactivé"
                        is TeamResult.Failure -> r.message
                    },
                )
            }
            // L'écran reflète toujours l'état du serveur
            load()
        }
    }

    fun showMessage(message: String) = _state.update { it.copy(message = message) }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}
