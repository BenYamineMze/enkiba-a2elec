package com.enkiba.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enkiba.app.models.ProductHistory
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.FilterPill
import com.enkiba.app.ui.components.SaleListRow
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SearchField
import com.enkiba.app.ui.components.SegmentedTabs
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.formatSaleMoment
import com.enkiba.app.ui.components.softShadow
import com.enkiba.app.ui.navigation.AppBottomBar
import com.enkiba.app.ui.navigation.AppTab
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing
import com.enkiba.app.ui.theme.Tile

data class HistoryActions(
    val onTab: (HistoryTab) -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onQuoteFilter: (QuoteFilter) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenSale: (SaleHeader) -> Unit = {},
    val onOpenQuote: (Quote) -> Unit = {},
    val onExport: () -> Unit = {},
    val onNavigate: (AppTab) -> Unit = {},
    val onNewSale: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(state: HistoryUiState, actions: HistoryActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); actions.onMessageShown() } }
    val tabs = state.tabs

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Historique",
                subtitle = if (state.isAdmin) "Toute la boutique" else "Mes ventes et mes devis",
                actions = {
                    if (state.tab != HistoryTab.QUOTES) {
                        if (state.exporting) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        else SquareIconButton(Icons.Rounded.PictureAsPdf, "Exporter en PDF", onClick = actions.onExport)
                    }
                },
            )
            SegmentedTabs(
                options = tabs.map { it.label },
                selectedIndex = tabs.indexOf(state.tab).coerceAtLeast(0),
                onSelect = { actions.onTab(tabs[it]) },
                modifier = Modifier.padding(horizontal = Spacing.screen),
            )
            SearchField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                placeholder = when (state.tab) {
                    HistoryTab.SALES -> "Référence, vendeur, paiement"
                    HistoryTab.QUOTES -> "Référence DEV-…, client"
                    HistoryTab.OPERATIONS -> "Article, opération, utilisateur"
                },
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.md),
            )
            if (state.tab == HistoryTab.QUOTES) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(QuoteFilter.entries) { f ->
                        FilterPill("${f.label} ${state.quoteCount(f)}", selected = state.quoteFilter == f, onClick = { actions.onQuoteFilter(f) })
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = actions.onRefresh,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = if (state.tab == HistoryTab.QUOTES) 0.dp else Spacing.md),
            ) {
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        when (state.tab) {
                            HistoryTab.SALES -> salesItems(state, actions)
                            HistoryTab.QUOTES -> quoteItems(state, actions)
                            HistoryTab.OPERATIONS -> operationItems(state)
                        }
                    }
                }
            }
        }
        AppBottomBar(
            selected = AppTab.HISTORY,
            isAdmin = state.isAdmin,
            onTab = actions.onNavigate,
            onNewSale = actions.onNewSale,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp))
    }
}

private fun LazyListScope.dayHeader(label: String, total: String? = null) {
    item(key = "day-$label") {
        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (total != null) Text(total, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LazyListScope.salesItems(state: HistoryUiState, actions: HistoryActions) {
    val sales = state.visibleSales
    if (sales.isEmpty()) {
        item { EmptyState(if (state.sales.isEmpty()) "Aucune vente pour l'instant." else "Aucune vente ne correspond à la recherche.") }
        return
    }
    HistoryFilters.groupByDay(sales, { it.saleDate }).forEach { g ->
        dayHeader(g.label, "${formatKmf(g.items.sumOf { it.netTotal })} KMF")
        items(g.items, key = { it.id }) { s -> SaleListRow(s, onClick = { actions.onOpenSale(s) }, showSeller = state.isAdmin) }
    }
}

private fun LazyListScope.quoteItems(state: HistoryUiState, actions: HistoryActions) {
    val quotes = state.visibleQuotes
    if (quotes.isEmpty()) {
        item {
            EmptyState(if (state.quotes.isEmpty()) "Aucun devis. Créez-en un depuis la caisse (bouton Devis)." else "Aucun devis ne correspond.")
        }
        return
    }
    items(quotes, key = { it.id }) { q -> QuoteCard(q, onClick = { actions.onOpenQuote(q) }) }
}

private fun LazyListScope.operationItems(state: HistoryUiState) {
    val ops = state.visibleOperations
    if (ops.isEmpty()) {
        item { EmptyState(if (state.operations.isEmpty()) "Aucune opération enregistrée." else "Aucune opération ne correspond.") }
        return
    }
    HistoryFilters.groupByDay(ops, { it.timestamp }).forEach { g ->
        dayHeader(g.label)
        items(g.items, key = { it.id }) { op -> OperationRow(op) }
    }
}

/** Tuile de couleur d'un statut de devis. */
@Composable
private fun quoteTile(q: Quote): Tile {
    val c = EnkibaTheme.colors
    return when {
        q.isExpired -> c.peach
        q.status == Quote.STATUS_ACCEPTED -> c.sky
        q.status == Quote.STATUS_CONVERTED -> c.mint
        q.status == Quote.STATUS_REJECTED -> c.danger
        else -> c.neutral
    }
}

@Composable
private fun QuoteCard(q: Quote, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    val converted = q.status == Quote.STATUS_CONVERTED
    Column(
        Modifier
            .fillMaxWidth()
            .softShadow(shape, 6.dp)
            .clip(shape)
            .background(if (converted) EnkibaTheme.colors.mint.container else MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(q.shortRef, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            StatusChip(q.statusLabel, quoteTile(q))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(q.clientName?.takeIf { it.isNotBlank() } ?: "Client non renseigné", style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        converted -> "Converti en vente"
                        q.isExpired -> "Expiré le ${q.validUntil}"
                        q.validUntil != null -> "Valable jusqu'au ${q.validUntil}"
                        else -> formatSaleMoment(q.createdAt)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (q.isExpired) EnkibaTheme.colors.peach.content else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(formatKmf(q.totalPrice), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OperationRow(op: ProductHistory) {
    val c = EnkibaTheme.colors
    val operation = op.operation.orEmpty()
    val tile = when {
        operation.contains("Négocié", ignoreCase = true) || operation.contains("RETOUR", ignoreCase = true) -> c.peach
        operation.startsWith("Vente", ignoreCase = true) -> c.mint
        operation.contains("Ajout", ignoreCase = true) -> c.sky
        operation.contains("Suppression", ignoreCase = true) || operation.contains("DELETE", ignoreCase = true) -> c.danger
        else -> c.neutral
    }
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(op.productName.orEmpty(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            StatusChip(operation.substringBefore(" (").ifBlank { "Info" }, tile)
        }
        val change = listOfNotNull(op.oldValue?.takeIf { it.isNotBlank() && it != "-" }, op.newValue?.takeIf { it.isNotBlank() }).joinToString(" → ")
        if (change.isNotEmpty()) Text(change, style = MaterialTheme.typography.bodyMedium)
        if (operation.contains("(")) {
            Text(operation.substringAfter("(").removeSuffix(")"), style = MaterialTheme.typography.bodySmall, color = c.peach.content)
        }
        Text("${op.username.orEmpty()} · ${formatSaleMoment(op.timestamp)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
