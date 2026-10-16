package com.enkiba.app.ui.users

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.TeamRepository
import com.enkiba.app.data.repository.TeamResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserFormUiState(
    val form: UserForm = UserForm(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    /** Fiche impossible à charger (hors ligne, introuvable) : l'écran le dit et propose de revenir. */
    val loadError: String? = null,
    /** Enregistré : l'écran se ferme et la liste affiche ce message. */
    val savedMessage: String? = null,
)

class UserFormViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TeamRepository(application)
    private val _state = MutableStateFlow(UserFormUiState())
    val state: StateFlow<UserFormUiState> = _state.asStateFlow()
    private var userId: String? = null
    private var started = false

    /** [id] null : création d'un vendeur ; sinon modification. */
    fun start(id: String?) {
        if (started) return
        started = true
        userId = id
        if (id == null) return
        _state.update { it.copy(loading = true, form = it.form.copy(isNew = false)) }
        viewModelScope.launch {
            when (val r = repository.member(id)) {
                is TeamResult.Ok -> _state.update {
                    it.copy(loading = false, form = UserForm(r.value.firstName, r.value.lastName, r.value.phone, r.value.email, isNew = false))
                }
                is TeamResult.Failure -> _state.update { it.copy(loading = false, loadError = r.message) }
            }
        }
    }

    fun update(transform: (UserForm) -> UserForm) = _state.update { it.copy(form = transform(it.form), error = null) }

    fun save() {
        val s = _state.value
        if (s.saving || s.loading) return
        s.form.validate()?.let { msg -> _state.update { it.copy(error = msg) }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val id = userId
            val r = if (id == null) repository.create(s.form) else repository.update(id, s.form)
            _state.update {
                when (r) {
                    is TeamResult.Ok -> it.copy(saving = false, savedMessage = if (id == null) "Vendeur créé" else "Modifications enregistrées")
                    is TeamResult.Failure -> it.copy(saving = false, error = r.message)
                }
            }
        }
    }
}
