package com.enkiba.app.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.ProfileRepository
import com.enkiba.app.data.repository.SaveProfileResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val email: String = "",
    val isAdmin: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    val firstNameMissing: Boolean get() = firstName.isBlank()
}

/** Règles de saisie du profil (testées en unitaire). */
object ProfileRules {
    const val MAX_NAME = 50

    fun cleanName(input: String): String = input.take(MAX_NAME)

    /** Téléphone : chiffres, espaces, « + » et « - » seulement. */
    fun cleanPhone(input: String): String = input.filter { it.isDigit() || it == ' ' || it == '+' || it == '-' }.take(20)
}

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProfileRepository(application)

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val p = repository.load()
            _state.update {
                it.copy(loading = false, firstName = p.firstName, lastName = p.lastName, phone = p.phone, email = p.email, isAdmin = p.isAdmin)
            }
        }
    }

    fun onFirstNameChange(v: String) = _state.update { it.copy(firstName = ProfileRules.cleanName(v), error = null, saved = false) }
    fun onLastNameChange(v: String) = _state.update { it.copy(lastName = ProfileRules.cleanName(v), error = null, saved = false) }
    fun onPhoneChange(v: String) = _state.update { it.copy(phone = ProfileRules.cleanPhone(v), error = null, saved = false) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        if (s.firstNameMissing) {
            _state.update { it.copy(error = "Le prénom est obligatoire") }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.save(s.firstName.trim(), s.lastName.trim(), s.phone.trim())) {
                SaveProfileResult.Saved -> _state.update { it.copy(saving = false, saved = true) }
                is SaveProfileResult.Failure -> _state.update { it.copy(saving = false, error = r.message) }
            }
        }
    }
}
