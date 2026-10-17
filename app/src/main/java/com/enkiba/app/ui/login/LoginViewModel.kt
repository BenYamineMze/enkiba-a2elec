package com.enkiba.app.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.AuthRepository
import com.enkiba.app.data.repository.LoginResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** État de l'écran de connexion. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val loggedIn: Boolean = false,
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AuthRepository(application)

    private val _state = MutableStateFlow(LoginUiState(loggedIn = repository.isLoggedIn()))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun login() {
        val s = _state.value
        if (s.loading) return
        val email = s.email.trim()
        if (email.isEmpty() || s.password.isEmpty()) {
            _state.update { it.copy(error = "Renseignez l'e-mail et le mot de passe") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.login(email, s.password)) {
                LoginResult.Success -> _state.update { it.copy(loading = false, loggedIn = true, password = "") }
                is LoginResult.Failure -> _state.update { it.copy(loading = false, error = result.message) }
            }
        }
    }
}
