package com.enkiba.app.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enkiba.app.models.Product
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.FilterPill
import com.enkiba.app.ui.components.ProgressBar
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SectionHeader
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.formatKmfCompact
import com.enkiba.app.ui.components.softShadow
import com.enkiba.app.ui.navigation.AppBottomBar
import com.enkiba.app.ui.navigation.AppTab
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

data class StatsActions(
    val onMonth: (MonthKey) -> Unit = {},
    val onProduct: (Product) -> Unit = {},
    val onNavigate: (AppTab) -> Unit = {},
    val onNewSale: () -> Unit = {},
)

@Composable
fun StatsScreen(state: StatsUiState, actions: StatsActions) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
          ScreenHeader(title = "Statistiques", subtitle = "Toute la boutique")
          LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
          ) {
            item { MonthCard(state, actions) }
            item { DaysCard(state) }
            item {
                SectionHeader(if (state.lowStockCount > 0) "État du stock · ${state.lowStockCount} en alerte" else "État du stock")
            }
            if (state.stock.isEmpty() && !state.loading) item { EmptyState("Aucun article dans le catalogue.") }
            items(state.stock, key = { it.product.id }) { line -> StockRow(line, onClick = { actions.onProduct(line.product) }) }
          }
        }
        AppBottomBar(
            selected = AppTab.STATS,
            isAdmin = true,
            onTab = actions.onNavigate,
            onNewSale = actions.onNewSale,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
        )
    }
}

/** Carte blanche arrondie des statistiques. */
@Composable
private fun StatsCard(content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.extraLarge
    Column(
        Modifier.fillMaxWidth().softShadow(shape, 10.dp).clip(shape).background(MaterialTheme.colorScheme.surface).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) { content() }
}

@Composable
private fun MonthCard(state: StatsUiState, actions: StatsActions) {
    val colors = EnkibaTheme.colors
    val stats = state.stats
    StatsCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            state.months.forEach { m -> FilterPill(m.shortLabel, selected = m == state.selected, onClick = { actions.onMonth(m) }) }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.loading || stats == null) {
                Box(Modifier.height(150.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            } else {
                SplitGauge(
                    transferShare = stats.transferShare,
                    cashShare = stats.cashShare,
                    transferColor = colors.warning,
                    cashColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.width(250.dp).height(140.dp).semantics {
                        contentDescription = "Espèces ${formatKmf(stats.cash)} KMF, virement ${formatKmf(stats.transfer)} KMF"
                    },
                )
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatKmfCompact(stats.net), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("KMF encaissés en ${state.selected.longLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (stats != null && !state.loading) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Legend(MaterialTheme.colorScheme.primary, "Espèces ${formatKmfCompact(stats.cash)}")
                Box(Modifier.width(Spacing.lg))
                Legend(colors.warning, "Virement ${formatKmfCompact(stats.transfer)}")
            }
            Text(
                if (stats.salesCount == 0) "Aucune vente ce mois-ci"
                else "${stats.salesCount} vente${if (stats.salesCount > 1) "s" else ""} · panier moyen ${formatKmf(stats.averageBasket)} KMF",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Demi-jauge : virements à gauche (orange), espèces à la suite (vert). */
@Composable
private fun SplitGauge(transferShare: Float, cashShare: Float, transferColor: Color, cashColor: Color, trackColor: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = 20.dp.toPx()
        val diameter = minOf(size.width, size.height * 2) - stroke
        val topLeft = Offset((size.width - diameter) / 2, stroke / 2)
        val arc = Size(diameter, diameter)
        val style = Stroke(stroke, cap = StrokeCap.Round)
        drawArc(trackColor, 180f, 180f, false, topLeft, arc, style = style)
        if (cashShare > 0f) drawArc(cashColor, 180f + 180f * transferShare, 180f * cashShare, false, topLeft, arc, style = style)
        // Virements dessinés en dernier : leur bout arrondi reste visible par-dessus les espèces
        if (transferShare > 0f) drawArc(transferColor, 180f, 180f * transferShare, false, topLeft, arc, style = style)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DaysCard(state: StatsUiState) {
    val stats = state.stats ?: return
    if (state.loading) return
    val colors = EnkibaTheme.colors
    val best = stats.bestDay
    StatsCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Encaissé par jour", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("en KMF", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (stats.days.isEmpty()) {
            Text("Aucune vente en ${state.selected.longLabel}.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            stats.days.forEach { d ->
                val isBest = d == best && stats.days.size > 1
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(d.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(58.dp))
                    ProgressBar(
                        progress = (d.total / stats.maxDay).toFloat(),
                        color = if (isBest) colors.warning else MaterialTheme.colorScheme.primary,
                        trackColor = if (isBest) colors.peach.container else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatKmf(d.total),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isBest) FontWeight.ExtraBold else FontWeight.Bold,
                        color = if (isBest) colors.peach.content else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(72.dp),
                    )
                }
            }
            if (stats.days.size > 1) {
                Text("En orange : meilleur jour du mois", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StockRow(line: StockLine, onClick: () -> Unit) {
    val colors = EnkibaTheme.colors
    val color = when {
        line.percent < 40 -> colors.danger.content
        line.percent < 60 -> colors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    val shape = MaterialTheme.shapes.medium
    Column(
        Modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f)) {
                Text(line.product.name.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                line.product.category?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (line.low) Icon(Icons.Rounded.WarningAmber, contentDescription = "Stock faible", tint = colors.peach.content, modifier = Modifier.size(20.dp))
            Text("${line.percent} %", style = MaterialTheme.typography.titleSmall, color = color)
        }
        ProgressBar(progress = line.percent / 100f, color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth())
        Text("Restant ${line.product.quantity} / ${line.total} · vendu ${line.product.soldQuantity}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
