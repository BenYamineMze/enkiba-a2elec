package com.enkiba.app.ui.switchshop

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enkiba.app.data.repository.MyShop
import com.enkiba.app.data.repository.ShopSwitchRepository
import com.enkiba.app.data.repository.SwitchResult
import com.enkiba.app.data.repository.TeamResult
import com.enkiba.app.pdf.PdfText
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.softShadow
import com.enkiba.app.ui.home.HomeActivity
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing
import com.enkiba.app.utils.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopSwitchUiState(
    val loading: Boolean = true,
    val shops: List<MyShop> = emptyList(),
    val error: String? = null,
    val confirm: MyShop? = null,
    /** Bascule en cours (synchro, vidage, téléchargement) : l'écran est bloqué. */
    val switching: MyShop? = null,
    val done: Boolean = false,
)

class ShopSwitchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ShopSwitchRepository(application)
    private val _state = MutableStateFlow(ShopSwitchUiState())
    val state: StateFlow<ShopSwitchUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            when (val r = repository.myShops()) {
                is TeamResult.Ok -> _state.update { it.copy(loading = false, shops = r.value, error = null) }
                is TeamResult.Failure -> _state.update { it.copy(loading = false, error = r.message) }
            }
        }
    }

    fun ask(shop: MyShop) { if (!shop.current && shop.available) _state.update { it.copy(confirm = shop, error = null) } }
    fun cancel() = _state.update { it.copy(confirm = null) }

    fun confirm() {
        val target = _state.value.confirm ?: return
        _state.update { it.copy(confirm = null, switching = target) }
        viewModelScope.launch {
            when (val r = repository.switchTo(target)) {
                SwitchResult.Done -> _state.update { it.copy(switching = null, done = true) }
                is SwitchResult.Failure -> _state.update { it.copy(switching = null, error = r.message) }
            }
        }
    }
}

/** Liste des boutiques de l'utilisateur et bascule vers l'une d'elles. */
class ShopSwitchActivity : ComponentActivity() {
    private val viewModel: ShopSwitchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!SessionManager(this).isLoggedIn) { finish(); return }
        if (savedInstanceState == null) viewModel.load()
        enableEdgeToEdge()
        setContent {
            EnkibaTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.done) {
                    if (state.done) {
                        // Tous les écrans repartent sur la nouvelle boutique
                        startActivity(Intent(this@ShopSwitchActivity, HomeActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    }
                }
                // Retour (bouton ou geste) bloqué pendant la bascule : la base locale est en cours de remplacement
                BackHandler(enabled = state.switching != null) {}
                ShopSwitchScreen(state, onBack = { if (state.switching == null) finish() }, onPick = viewModel::ask,
                    onConfirm = viewModel::confirm, onCancel = viewModel::cancel)
            }
        }
    }
}

@Composable
fun ShopSwitchScreen(state: ShopSwitchUiState, onBack: () -> Unit, onPick: (MyShop) -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = EnkibaTheme.colors
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(title = "Changer de boutique", onBack = onBack)
            Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    state.shops.isEmpty() && state.error != null -> EmptyState(state.error)
                    else -> {
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                        state.shops.forEach { s ->
                            val shape = MaterialTheme.shapes.large
                            val tile = when { s.current -> colors.mint; !s.available -> colors.neutral; else -> colors.sky }
                            Row(
                                Modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
                                    .clickable(enabled = !s.current && s.available && state.switching == null, role = Role.Button) { onPick(s) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            ) {
                                Box(Modifier.size(42.dp).clip(MaterialTheme.shapes.medium).background(tile.container), contentAlignment = Alignment.Center) {
                                    Text(PdfText.initials(s.name), style = MaterialTheme.typography.titleSmall, color = tile.content)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleSmall)
                                    Text(if (s.isAdmin) "Admin" else "Vendeur", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                when {
                                    s.current -> StatusChip("Actuelle", colors.mint)
                                    !s.available -> StatusChip("Suspendue", colors.neutral)
                                    state.switching?.shopId == s.shopId -> CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Text(
                            "Les ventes non envoyées sont synchronisées avant le changement. Les données de la boutique choisie sont ensuite téléchargées : une connexion internet est nécessaire.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xl),
                        )
                    }
                }
            }
        }
    }
    state.confirm?.let { s ->
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Passer sur ${s.name} ?") },
            text = { Text("Les changements en attente sont d'abord envoyés. La caisse, les articles et l'historique afficheront ensuite ${s.name}.") },
            confirmButton = { TextButton(onClick = onConfirm) { Text("Changer") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } },
        )
    }
    state.switching?.let { s ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Passage sur ${s.name}…") },
            text = { Text("Synchronisation et téléchargement des données en cours.") },
            confirmButton = {},
        )
    }
}
