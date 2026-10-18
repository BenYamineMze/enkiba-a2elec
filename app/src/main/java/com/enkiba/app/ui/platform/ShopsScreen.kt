package com.enkiba.app.ui.platform

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBusiness
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
import com.enkiba.app.models.ShopOverview
import com.enkiba.app.pdf.PdfText
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.formatKmfCompact
import com.enkiba.app.ui.components.softShadow
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

/** Écran « Boutiques » du super-administrateur. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsScreen(
    state: ShopsUiState,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onOpen: (ShopOverview) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); onMessageShown() } }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Boutiques",
                subtitle = if (state.loading || state.error != null) null else buildString {
                    append("${state.shops.size} boutique${if (state.shops.size > 1) "s" else ""}")
                    if (state.suspendedCount > 0) append(" · ${state.suspendedCount} suspendue${if (state.suspendedCount > 1) "s" else ""}")
                },
                onBack = onBack,
                actions = { SquareIconButton(Icons.Rounded.AddBusiness, "Nouvelle boutique", onClick = onCreate) },
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    state.error != null -> LazyColumn(Modifier.fillMaxSize()) {
                        item { EmptyState(state.error, actionLabel = "Réessayer", onAction = onRetry) }
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.shops, key = { it.shopId }) { s -> ShopRow(s, onClick = { onOpen(s) }) }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }
}

@Composable
private fun ShopRow(s: ShopOverview, onClick: () -> Unit) {
    val colors = EnkibaTheme.colors
    val shape = MaterialTheme.shapes.large
    val tile = if (s.isSuspended) colors.neutral else colors.mint
    Row(
        Modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClickLabel = "Ouvrir la fiche", onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(46.dp).clip(MaterialTheme.shapes.medium).background(tile.container), contentAlignment = Alignment.Center) {
            Text(PdfText.initials(s.name.orEmpty()), style = MaterialTheme.typography.titleSmall, color = tile.content)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(s.name.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (s.isSuspended) StatusChip("Suspendue", colors.neutral)
            }
            Text(
                "${s.members} membre${if (s.members > 1) "s" else ""} · ${s.products} article${if (s.products > 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatKmfCompact(s.revenueMonth), style = MaterialTheme.typography.titleSmall)
            Text("${s.salesMonth} vente${if (s.salesMonth > 1) "s" else ""} ce mois", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
