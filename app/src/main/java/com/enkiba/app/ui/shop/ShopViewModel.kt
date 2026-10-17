package com.enkiba.app.ui.shop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.local.ShopDetails
import com.enkiba.app.data.repository.SaveShopResult
import com.enkiba.app.data.repository.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopUiState(
    val form: ShopForm = ShopForm(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    /** Modifié depuis l'ouverture : on ne remplace plus la saisie par la version du serveur. */
    val edited: Boolean = false,
)

class ShopViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShopRepository(application)
    private val _state = MutableStateFlow(ShopUiState(form = repository.cached().toForm()))
    val state: StateFlow<ShopUiState> = _state.asStateFlow()

    init {
        // Le cache s'affiche tout de suite ; la version du serveur le remplace si rien n'a été saisi
        viewModelScope.launch {
            repository.refresh()?.let { fresh -> _state.update { if (it.edited) it else it.copy(form = fresh.toForm()) } }
        }
    }

    fun update(transform: (ShopForm) -> ShopForm) =
        _state.update { it.copy(form = transform(it.form), error = null, saved = false, edited = true) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        s.form.validate()?.let { msg -> _state.update { it.copy(error = msg) }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.save(s.form)) {
                SaveShopResult.Saved -> _state.update { it.copy(saving = false, saved = true, edited = false) }
                is SaveShopResult.Failure -> _state.update { it.copy(saving = false, error = r.message) }
            }
        }
    }

    private fun ShopDetails.toForm() =
        ShopForm(name = if (this == ShopDetails.UNKNOWN) "" else name, address = address, phone = phone, email = email)
}
