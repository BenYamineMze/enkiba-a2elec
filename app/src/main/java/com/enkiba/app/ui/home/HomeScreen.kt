package com.enkiba.app.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enkiba.app.data.repository.HomeData
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.ui.components.AmountText
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.IconBadge
import com.enkiba.app.ui.components.ListRow
import com.enkiba.app.ui.components.ProgressRing
import com.enkiba.app.ui.components.SaleListRow
import com.enkiba.app.ui.components.SectionHeader
import com.enkiba.app.ui.components.StatTile
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.UserHeader
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.formatKmfCompact
import com.enkiba.app.ui.components.formatSaleMoment
import com.enkiba.app.ui.navigation.AppBottomBar
import com.enkiba.app.ui.navigation.AppTab
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

/** Actions de l'accueil, branchées par HomeActivity. */
data class HomeActions(
    val onSync: () -> Unit = {},
    val onOpenTab: (AppTab) -> Unit = {},
    val onNewSale: () -> Unit = {},
    val onTodaySales: () -> Unit = {},
    val onLowStock: () -> Unit = {},
    val onOpenQuotes: () -> Unit = {},
    val onStockValue: () -> Unit = {},
    val onAllSales: () -> Unit = {},
    val onOpenSale: (SaleHeader) -> Unit = {},
    val onManageUsers: () -> Unit = {},
    val onProfile: () -> Unit = {},
    /** Build debug seulement (null en release) : ouvre le catalogue du système de design. */
    val onOpenDesignCatalog: (() -> Unit)? = null,
    val onLogout: () -> Unit = {},
)

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item { Header(state, actions) }
            val data = state.data
            if (data == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                item { TodayCard(data, Modifier.padding(horizontal = Spacing.screen)) }
                item { Tiles(data, state.isAdmin, actions, Modifier.padding(horizontal = Spacing.screen)) }
                item {
                    SectionHeader(
                        if (state.isAdmin) "Dernières ventes" else "Mes dernières ventes",
                        modifier = Modifier.padding(horizontal = Spacing.screen),
                        actionLabel = "Tout voir",
                        onAction = actions.onAllSales,
                    )
                }
                if (data.recentSales.isEmpty()) {
                    item { EmptyState("Aucune vente pour l'instant.", actionLabel = "Nouvelle vente", onAction = actions.onNewSale) }
                } else {
                    items(data.recentSales, key = { it.id }) { sale ->
                        SaleListRow(sale, onClick = { actions.onOpenSale(sale) }, modifier = Modifier.padding(horizontal = Spacing.screen), showSeller = state.isAdmin)
                    }
                }
            }
        }

        AppBottomBar(
            selected = AppTab.HOME,
            isAdmin = state.isAdmin,
            onTab = actions.onOpenTab,
            onNewSale = actions.onNewSale,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

@Composable
private fun Header(state: HomeUiState, actions: HomeActions) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        UserHeader(
            firstName = state.firstName,
            lastName = state.lastName,
            email = state.email,
            roleLabel = if (state.isAdmin) "Admin" else "Vendeur",
            syncState = state.syncState,
            onSync = actions.onSync,
            onAvatarClick = { menuOpen = true },
        )
        Box(Modifier.align(Alignment.TopEnd).padding(end = Spacing.screen, top = 56.dp)) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Mon profil") }, onClick = { menuOpen = false; actions.onProfile() })
                if (state.isAdmin) {
                    DropdownMenuItem(text = { Text("Gestion des utilisateurs") }, onClick = { menuOpen = false; actions.onManageUsers() })
                }
                actions.onOpenDesignCatalog?.let { open ->
                    DropdownMenuItem(text = { Text("Catalogue design (dev)") }, onClick = { menuOpen = false; open() })
                }
                DropdownMenuItem(text = { Text("Déconnexion") }, onClick = { menuOpen = false; actions.onLogout() })
            }
        }
    }
}

/** Carte « Encaissé aujourd'hui » : anneau = part des espèces, évolution par rapport à hier. */
@Composable
private fun TodayCard(data: HomeData, modifier: Modifier = Modifier) {
    val colors = EnkibaTheme.colors
    EnkibaCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val hasSales = data.todayNet > 0.0
            ProgressRing(
                progress = data.cashShare,
                color = MaterialTheme.colorScheme.primary,
                trackColor = if (hasSales) colors.warning else MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (hasSales) "${Math.round(data.cashShare * 100)} %" else "—",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text("en espèces", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Encaissé aujourd'hui", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountText(data.todayNet)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("● ") }
                        append("Espèces ${formatKmf(data.todayCash)}\n")
                        withStyle(SpanStyle(color = colors.warning)) { append("● ") }
                        append("Virement ${formatKmf(data.todayTransfer)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                data.changeVsYesterdayPercent?.let { pct ->
                    StatusChip(
                        (if (pct >= 0) "+$pct" else "$pct") + " % vs hier",
                        if (pct >= 0) colors.mint else colors.peach,
                    )
                }
            }
        }
    }
}

@Composable
private fun Tiles(data: HomeData, isAdmin: Boolean, actions: HomeActions, modifier: Modifier = Modifier) {
    val colors = EnkibaTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(colors.mint, Icons.Rounded.ShoppingBag, data.todaySalesCount.toString(), "Ventes du jour",
                Modifier.weight(1f), onClick = actions.onTodaySales)
            StatTile(colors.peach, Icons.Rounded.Warning, data.lowStockCount.toString(), "Stock faible",
                Modifier.weight(1f), onClick = actions.onLowStock)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(colors.sky, Icons.Rounded.Description, data.openQuotesCount.toString(), "Devis en cours",
                Modifier.weight(1f), onClick = actions.onOpenQuotes)
            if (isAdmin) {
                StatTile(colors.lavender, Icons.Rounded.BarChart, formatKmfCompact(data.stockValue), "Valeur du stock (KMF)",
                    Modifier.weight(1f), onClick = actions.onStockValue)
            } else {
                StatTile(colors.lavender, Icons.Rounded.Inventory2, data.productCount.toString(), "Articles en catalogue",
                    Modifier.weight(1f), onClick = actions.onStockValue)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun HomePreview() {
    val sale = SaleHeader().apply {
        totalPrice = 12_500.0; paymentMethod = "espece"; saleDate = "2026-10-16T11:32Z"
    }
    EnkibaTheme {
        HomeScreen(
            HomeUiState(
                firstName = "Mze", lastName = "Ben", isAdmin = true,
                data = HomeData(184_500.0, 136_500.0, 48_000.0, 164_000.0, 23, 7, 4, 142, 1_240_000.0, listOf(sale), 0),
            ),
            HomeActions(),
        )
    }
}
