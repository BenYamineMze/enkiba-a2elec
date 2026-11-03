package com.enkiba.app.ui.stats

import com.enkiba.app.ui.components.SquareIconButton
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
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
    /** Liste des produits sans prix d'achat. */
    val onMissingCost: () -> Unit = {},
    /** Export comptable (Excel) du mois choisi. */
    val onExport: (MonthKey) -> Unit = {},
)

@Composable
fun StatsScreen(state: StatsUiState, actions: StatsActions) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
          var exportDialog by rememberSaveable { mutableStateOf(false) }
          if (exportDialog) {
              ExportDialog(state.months, state.selected, onDismiss = { exportDialog = false }) { m ->
                  exportDialog = false
                  actions.onExport(m)
              }
          }
          ScreenHeader(
              title = "Statistiques",
              subtitle = "Toute la boutique",
              actions = {
                  if (state.exporting) {
                      CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                  } else {
                      SquareIconButton(Icons.Rounded.FileDownload, "Export comptable (Excel)", onClick = { exportDialog = true })
                  }
              },
          )
          LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
          ) {
            item { MonthCard(state, actions) }
            state.margin?.takeIf { !state.loading }?.let { m -> item { MarginCard(m, state.selected.longLabel, actions) } }
            item { DaysCard(state) }
            state.stockValue?.let { v -> item { StockValueCard(v, actions) } }
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

/** Marge brute du mois et produits qui rapportent le plus (prix d'achat figé à la vente). */
@Composable
private fun MarginCard(m: MarginStats, monthLabel: String, actions: StatsActions) {
    val colors = EnkibaTheme.colors
    StatsCard {
        Text("Marge brute", style = MaterialTheme.typography.titleMedium)
        if (!m.hasData) {
            Text(
                if (m.uncoveredLines > 0) "Aucune vente de $monthLabel n'a de prix d'achat : saisissez-les dans les fiches produit."
                else "Aucune vente en $monthLabel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    formatKmfCompact(m.margin),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (m.margin < 0) colors.danger.content else MaterialTheme.colorScheme.onSurface,
                )
                Text("KMF", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp))
                Box(Modifier.weight(1f))
                m.rate?.let { Text("${Math.round(it * 100)} % du chiffre", style = MaterialTheme.typography.titleSmall, color = colors.link) }
            }
            if (m.top.isNotEmpty()) {
                Text("Ceux qui rapportent le plus", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                m.top.forEach { p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(p.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("× ${p.sold}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${formatKmf(p.margin)} KMF", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, modifier = Modifier.width(96.dp))
                    }
                }
            }
        }
        if (m.uncoveredLines > 0) {
            MissingCostNote(
                "${m.uncoveredLines} ligne${if (m.uncoveredLines > 1) "s" else ""} de vente sans prix d'achat " +
                    "(${formatKmf(m.uncoveredRevenue)} KMF) non comptée${if (m.uncoveredLines > 1) "s" else ""}",
                actions.onMissingCost,
            )
        }
    }
}

/** Valeur du stock au prix de vente et au prix d'achat. */
@Composable
private fun StockValueCard(v: StockValue, actions: StatsActions) {
    StatsCard {
        Text("Valeur du stock", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ValueColumn("Au prix de vente", v.atSalePrice, Modifier.weight(1f))
            ValueColumn("Au prix d'achat", v.atCost, Modifier.weight(1f))
        }
        if (v.missingCost > 0) {
            MissingCostNote(
                "${v.missingCost} produit${if (v.missingCost > 1) "s" else ""} sans prix d'achat, non compté${if (v.missingCost > 1) "s" else ""} au prix d'achat",
                actions.onMissingCost,
            )
        }
    }
}

@Composable
private fun ValueColumn(label: String, amount: Double, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${formatKmfCompact(amount)} KMF", style = MaterialTheme.typography.titleLarge)
    }
}

/** Avertissement orange qui mène à la liste des produits sans prix d'achat. */
@Composable
private fun MissingCostNote(text: String, onClick: () -> Unit) {
    val colors = EnkibaTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(colors.peach.container)
            .clickable(role = Role.Button, onClickLabel = "Voir les produits sans prix d'achat", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = colors.peach.content, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.peach.content, modifier = Modifier.weight(1f))
    }
}

/** Choix du mois à exporter ; le classeur contient ventes, lignes, retours, stock et marge. */
@Composable
private fun ExportDialog(months: List<MonthKey>, selected: MonthKey, onDismiss: () -> Unit, onExport: (MonthKey) -> Unit) {
    var choice by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export comptable") },
        text = {
            Column(Modifier.selectableGroup()) {
                Text(
                    "Classeur Excel : synthèse, ventes, lignes, retours, stock et marge.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
                months.asReversed().forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Spacing.touch)
                            .selectable(selected = m == choice, role = Role.RadioButton) { choice = m },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        RadioButton(selected = m == choice, onClick = null)
                        Text("${m.longLabel.replaceFirstChar { it.uppercase() }} ${m.year}", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onExport(choice) }) { Text("Créer et partager") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
