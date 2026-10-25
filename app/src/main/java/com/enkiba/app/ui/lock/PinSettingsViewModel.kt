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

enum class PinStep { OVERVIEW, CURRENT, NEW, CONFIRM }

/** Ce que l'on fera une fois le code actuel vérifié. */
enum class PinAction { CHANGE, DISABLE }

data class PinSettingsUiState(
    val enabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricOn: Boolean = false,
    val step: PinStep = PinStep.NEW,
    val action: PinAction? = null,
    val entered: String = "",
    val firstPin: String = "",
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val closed: Boolean = false,
    val loggedOut: Boolean = false,
)

/** Activer, changer ou désactiver le code PIN ; empreinte en option. */
class PinSettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val session = SessionManager(app)
    private val _state = MutableStateFlow(initial())
    val state: StateFlow<PinSettingsUiState> = _state.asStateFlow()

    private fun initial(): PinSettingsUiState {
        val enabled = AppLock.isEnabled(session)
        return PinSettingsUiState(
            enabled = enabled,
            biometricAvailable = Biometrics.available(getApplication()),
            biometricOn = session.isBiometricEnabled,
            step = if (enabled) PinStep.OVERVIEW else PinStep.NEW,
        )
    }

    fun start(action: PinAction) =
        _state.update { it.copy(step = PinStep.CURRENT, action = action, entered = "", error = null, message = null) }

    fun setBiometric(on: Boolean) {
        session.isBiometricEnabled = on
        _state.update { it.copy(biometricOn = on) }
    }

    /** Retour : on revient au résumé, ou on quitte si le code n'est pas encore activé. */
    fun back() = _state.update {
        if (it.step == PinStep.OVERVIEW || !it.enabled) it.copy(closed = true)
        else it.copy(step = PinStep.OVERVIEW, entered = "", firstPin = "", error = null)
    }

    fun onDelete() = _state.update { it.copy(entered = it.entered.dropLast(1), error = null) }

    fun onDigit(d: Char) {
        val s = _state.value
        if (s.busy || s.step == PinStep.OVERVIEW || s.entered.length >= AppLock.PIN_LENGTH) return
        val entered = s.entered + d
        _state.update { it.copy(entered = entered, error = null) }
        if (entered.length < AppLock.PIN_LENGTH) return
        when (s.step) {
            PinStep.CURRENT -> checkCurrent(entered)
            PinStep.NEW -> _state.update {
                if (PinRules.isTooSimple(entered)) it.copy(entered = "", error = "Code trop simple : évitez 1234, 0000…")
                else it.copy(step = PinStep.CONFIRM, firstPin = entered, entered = "")
            }
            PinStep.CONFIRM -> if (entered == s.firstPin) save(entered) else _state.update {
                it.copy(step = PinStep.NEW, entered = "", firstPin = "", error = "Les deux codes sont différents. Recommencez.")
            }
            PinStep.OVERVIEW -> Unit
        }
    }

    private fun checkCurrent(pin: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { AppLock.check(session, pin) }
            _state.update {
                when (result) {
                    PinResult.Ok -> if (it.action == PinAction.DISABLE) {
                        AppLock.disable(session)
                        it.copy(busy = false, closed = true, message = "Code PIN désactivé")
                    } else {
                        it.copy(busy = false, step = PinStep.NEW, entered = "")
                    }
                    is PinResult.Wrong -> it.copy(busy = false, entered = "", error = "Code incorrect. Encore ${result.remaining} essais.")
                    PinResult.TooManyAttempts -> it.copy(busy = false, loggedOut = true)
                }
            }
        }
    }

    private fun save(pin: String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            withContext(Dispatchers.Default) { AppLock.enable(session, pin) }
            _state.update {
                it.copy(
                    busy = false, enabled = true, step = PinStep.OVERVIEW, entered = "", firstPin = "",
                    message = if (it.enabled) "Code PIN modifié" else "Code PIN activé",
                )
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

/** Codes refusés : tous les chiffres identiques ou une suite (1234, 9876). */
object PinRules {
    fun isTooSimple(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val steps = pin.zipWithNext { a, b -> b - a }.toSet()
        return steps == setOf(1) || steps == setOf(-1)
    }
}
