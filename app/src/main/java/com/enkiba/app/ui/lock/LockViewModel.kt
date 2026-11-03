package com.enkiba.app.ui.lock

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.security.AppLock
import com.enkiba.app.security.PinResult
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LockUiState(
    val entered: String = "",
    val error: String? = null,
    val checking: Boolean = false,
    val unlocked: Boolean = false,
    val loggedOut: Boolean = false,
    val tooManyAttempts: Boolean = false,
)

/** Saisie du code sur l'écran de verrouillage. */
class LockViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    fun onDigit(d: Char) {
        val s = _state.value
        if (s.checking || s.entered.length >= AppLock.PIN_LENGTH) return
        val entered = s.entered + d
        _state.update { it.copy(entered = entered, error = null) }
        if (entered.length == AppLock.PIN_LENGTH) check(entered)
    }

    fun onDelete() = _state.update { it.copy(entered = it.entered.dropLast(1), error = null) }

    fun onBiometricSuccess() {
        session.pinFailures = 0
        _state.update { it.copy(unlocked = true) }
    }

    fun forgot() = _state.update { it.copy(loggedOut = true) }

    private fun check(pin: String) {
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            // PBKDF2 : quelques dizaines de millisecondes, hors du thread principal
            val result = withContext(Dispatchers.Default) { AppLock.check(session, pin) }
            _state.update {
                when (result) {
                    PinResult.Ok -> it.copy(checking = false, unlocked = true)
                    is PinResult.Wrong -> it.copy(
                        checking = false,
                        entered = "",
                        error = if (result.remaining == 1) "Code incorrect. Dernier essai avant la déconnexion."
                        else "Code incorrect. Encore ${result.remaining} essais.",
                    )
                    PinResult.TooManyAttempts -> it.copy(checking = false, loggedOut = true, tooManyAttempts = true)
                }
            }
        }
    }
}
