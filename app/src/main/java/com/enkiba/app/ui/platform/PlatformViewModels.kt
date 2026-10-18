package com.enkiba.app.ui.platform

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.PlatformRepository
import com.enkiba.app.data.repository.TeamResult
import com.enkiba.app.models.ShopMemberOverview
import com.enkiba.app.models.ShopOverview
import com.enkiba.app.ui.shop.ShopForm
import com.enkiba.app.ui.users.UserForm
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Liste des boutiques -------------------------------------------------------------

data class ShopsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val shops: List<ShopOverview> = emptyList(),
    val error: String? = null,
    val message: String? = null,
) {
    val suspendedCount: Int get() = shops.count { it.isSuspended }
}

class ShopsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlatformRepository(application)
    private val _state = MutableStateFlow(ShopsUiState())
    val state: StateFlow<ShopsUiState> = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        if (refresh) _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            when (val r = repository.shops()) {
                is TeamResult.Ok -> _state.update { it.copy(loading = false, refreshing = false, shops = r.value, error = null) }
                is TeamResult.Failure -> _state.update {
                    if (it.shops.isEmpty()) it.copy(loading = false, refreshing = false, error = r.message)
                    else it.copy(loading = false, refreshing = false, message = r.message)
                }
            }
        }
    }

    fun showMessage(m: String) = _state.update { it.copy(message = m) }
    fun consumeMessage() = _state.update { it.copy(message = null) }
}

// --- Fiche d'une boutique ------------------------------------------------------------

data class ShopAdminUiState(
    val loading: Boolean = true,
    val shop: ShopOverview? = null,
    val members: List<ShopMemberOverview> = emptyList(),
    val form: ShopForm = ShopForm(),
    val edited: Boolean = false,
    val saving: Boolean = false,
    val confirmSuspend: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val notFound: Boolean = false,
)

class ShopAdminViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlatformRepository(application)
    private val _state = MutableStateFlow(ShopAdminUiState())
    val state: StateFlow<ShopAdminUiState> = _state.asStateFlow()
    private var shopId: String? = null

    fun load(id: String) {
        shopId = id
        viewModelScope.launch {
            when (val r = repository.shops()) {
                is TeamResult.Ok -> {
                    val shop = r.value.firstOrNull { it.shopId == id }
                    _state.update { s ->
                        s.copy(
                            loading = false, shop = shop, notFound = shop == null,
                            form = if (s.edited || shop == null) s.form else ShopForm(shop.name.orEmpty(), shop.address.orEmpty(), shop.phone.orEmpty(), shop.email.orEmpty()),
                        )
                    }
                    if (shop != null) (repository.members(id) as? TeamResult.Ok)?.let { m -> _state.update { it.copy(members = m.value) } }
                }
                is TeamResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun update(transform: (ShopForm) -> ShopForm) = _state.update { it.copy(form = transform(it.form), edited = true, error = null) }

    fun save() {
        val id = shopId ?: return
        val s = _state.value
        if (s.saving) return
        s.form.validate()?.let { msg -> _state.update { it.copy(error = msg) }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.update(id, s.form)) {
                is TeamResult.Ok -> { _state.update { it.copy(saving = false, edited = false, message = "Coordonnées enregistrées") }; load(id) }
                is TeamResult.Failure -> _state.update { it.copy(saving = false, error = r.message) }
            }
        }
    }

    fun askSuspend() = _state.update { it.copy(confirmSuspend = true) }
    fun cancelSuspend() = _state.update { it.copy(confirmSuspend = false) }

    /** Suspend une boutique active, ou réactive une boutique suspendue. */
    fun toggleSuspension() {
        val id = shopId ?: return
        val suspend = _state.value.shop?.isSuspended == false
        _state.update { it.copy(confirmSuspend = false, saving = true) }
        viewModelScope.launch {
            when (val r = repository.setSuspended(id, suspend)) {
                is TeamResult.Ok -> { _state.update { it.copy(saving = false, message = if (suspend) "Boutique suspendue" else "Boutique réactivée") }; load(id) }
                is TeamResult.Failure -> _state.update { it.copy(saving = false, message = r.message) }
            }
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }
}

// --- Création d'une boutique --------------------------------------------------------

data class CreateShopUiState(
    val shop: ShopForm = ShopForm(),
    val admin: UserForm = UserForm(isNew = true),
    val saving: Boolean = false,
    val error: String? = null,
    val created: Boolean = false,
)

class CreateShopViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlatformRepository(application)
    private val _state = MutableStateFlow(CreateShopUiState())
    val state: StateFlow<CreateShopUiState> = _state.asStateFlow()

    fun updateShop(t: (ShopForm) -> ShopForm) = _state.update { it.copy(shop = t(it.shop), error = null) }
    fun updateAdmin(t: (UserForm) -> UserForm) = _state.update { it.copy(admin = t(it.admin), error = null) }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val problem = s.shop.validate() ?: s.admin.validate()?.let { "Administrateur : ${it.replaceFirstChar { c -> c.lowercase() }}" }
        if (problem != null) { _state.update { it.copy(error = problem) }; return }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            when (val r = repository.create(s.shop, s.admin)) {
                is TeamResult.Ok -> _state.update { it.copy(saving = false, created = true) }
                is TeamResult.Failure -> _state.update { it.copy(saving = false, error = r.message) }
            }
        }
    }
}
