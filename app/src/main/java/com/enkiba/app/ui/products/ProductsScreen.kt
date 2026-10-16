package com.enkiba.app.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enkiba.app.models.Product
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.ListRow
import com.enkiba.app.ui.components.ProgressRing
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SearchField
import com.enkiba.app.ui.components.SegmentedTabs
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.formatKmfCompact
import com.enkiba.app.ui.navigation.AppBottomBar
import com.enkiba.app.ui.navigation.AppTab
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

data class ProductsActions(
    val onQueryChange: (String) -> Unit = {},
    val onTabChange: (StockTab) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpen: (Product) -> Unit = {},
    val onEdit: (Product) -> Unit = {},
    val onAskDelete: (Product) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onQrList: () -> Unit = {},
    val onNavigate: (AppTab) -> Unit = {},
    val onNewSale: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(state: ProductsUiState, actions: ProductsActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); actions.onMessageShown() }
    }
    val total = state.count(StockTab.ALL)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Produits",
                subtitle = buildString {
                    append("$total article${if (total > 1) "s" else ""}")
                    if (state.isAdmin) append(" · ${formatKmfCompact(state.stockValue)} KMF en stock")
                },
                actions = {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SquareIconButton(Icons.Rounded.QrCode2, "Liste des codes QR", onClick = actions.onQrList)
                        // Seul un admin crée des articles (règle du serveur)
                        if (state.isAdmin) SquareIconButton(Icons.Rounded.Add, "Ajouter un produit", onClick = actions.onAdd, filled = true)
                    }
                },
            )
            SearchField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                placeholder = "Nom, catégorie ou code QR",
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
            SegmentedTabs(
                options = StockTab.entries.map { t -> if (t == StockTab.ALL) t.label else "${t.label} · ${state.count(t)}" },
                selectedIndex = state.tab.ordinal,
                onSelect = { actions.onTabChange(StockTab.entries[it]) },
                modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
            )

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    state.visible.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            EmptyState(
                                when {
                                    state.products.isEmpty() -> "Aucun article dans le catalogue."
                                    state.query.isNotBlank() -> "Aucun article ne correspond à la recherche."
                                    state.tab == StockTab.OUT -> "Aucun article en rupture."
                                    state.tab == StockTab.LOW -> "Aucun article en stock faible."
                                    else -> "Aucun article."
                                },
                                actionLabel = if (state.isAdmin && state.products.isEmpty()) "Ajouter un produit" else null,
                                onAction = actions.onAdd,
                            )
                        }
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.visible, key = { it.id }) { p -> ProductRow(p, state.isAdmin, actions) }
                    }
                }
            }
        }

        AppBottomBar(
            selected = AppTab.PRODUCTS,
            isAdmin = state.isAdmin,
            onTab = actions.onNavigate,
            onNewSale = actions.onNewSale,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))
    }

    state.pendingDelete?.let { p ->
        AlertDialog(
            onDismissRequest = actions.onCancelDelete,
            title = { Text("Supprimer ${p.name} ?") },
            text = { Text("L'article disparaîtra du catalogue sur tous les téléphones. Les ventes passées restent intactes.") },
            confirmButton = { TextButton(onClick = actions.onConfirmDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = actions.onCancelDelete) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ProductRow(p: Product, isAdmin: Boolean, actions: ProductsActions) {
    val colors = EnkibaTheme.colors
    val level = ProductFilters.levelOf(p)
    val ratio = ProductFilters.stockRatio(p)
    val (ringColor, trackColor, textColor) = when (level) {
        StockLevel.OK -> Triple(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onSurface)
        StockLevel.LOW -> Triple(colors.warning, colors.peach.container, colors.peach.content)
        StockLevel.OUT -> Triple(colors.danger.content, colors.danger.container, colors.danger.content)
    }
    val reference = if (p.initial_stock > 0) " sur ${p.initial_stock}" else ""
    ListRow(
        title = p.name.orEmpty(),
        subtitle = when (level) {
            StockLevel.OK -> "${p.category.orEmpty()} · ${p.quantity}$reference"
            StockLevel.LOW -> "Stock faible · ${p.quantity}$reference"
            StockLevel.OUT -> "Rupture de stock"
        },
        subtitleColor = if (level == StockLevel.OK) MaterialTheme.colorScheme.onSurfaceVariant else textColor,
        onClick = { actions.onOpen(p) },
        leading = {
            ProgressRing(ratio, size = 52.dp, strokeWidth = 6.dp, color = ringColor, trackColor = trackColor) {
                Text("${Math.round(ratio * 100)}%", style = MaterialTheme.typography.labelSmall, color = textColor)
            }
        },
        trailing = {
            Text(formatKmf(p.price), style = MaterialTheme.typography.bodyLarge)
            if (isAdmin) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Actions pour ${p.name}")
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(text = { Text("Modifier") }, onClick = { open = false; actions.onEdit(p) })
                        DropdownMenuItem(
                            text = { Text("Supprimer", color = MaterialTheme.colorScheme.error) },
                            onClick = { open = false; actions.onAskDelete(p) },
                        )
                    }
                }
            }
        },
    )
}
