package com.enkiba.app.ui.products

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.enkiba.app.models.Product
import com.enkiba.app.ui.components.AmountText
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ProgressRing
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SecondaryButton
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.StatTile
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.formatKmfCompact
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

@Composable
fun ProductDetailScreen(
    state: ProductDetailUiState,
    onBack: () -> Unit,
    onSell: (Product) -> Unit,
    onEdit: (Product) -> Unit,
) {
    val colors = EnkibaTheme.colors
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            val p = state.product
            ScreenHeader(
                title = p?.name ?: "Article",
                subtitle = p?.category,
                onBack = onBack,
                actions = { if (state.isAdmin && p != null) SquareIconButton(Icons.Rounded.Edit, "Modifier l'article", onClick = { onEdit(p) }) },
            )
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.notFound || p == null -> EmptyState("Cet article n'existe plus.", actionLabel = "Retour", onAction = onBack)
                else -> Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    val level = ProductFilters.levelOf(p)
                    // Stock et prix
                    EnkibaCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            val (ring, track) = when (level) {
                                StockLevel.OK -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
                                StockLevel.LOW -> colors.warning to colors.peach.container
                                StockLevel.OUT -> colors.danger.content to colors.danger.container
                            }
                            ProgressRing(ProductFilters.stockRatio(p), size = 112.dp, strokeWidth = 10.dp, color = ring, trackColor = track) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${p.quantity}", style = MaterialTheme.typography.titleLarge)
                                    Text("en stock", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Prix unitaire", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                AmountText(p.price)
                                when (level) {
                                    StockLevel.OK -> StatusChip("Stock normal", colors.mint)
                                    StockLevel.LOW -> StatusChip("Stock faible", colors.peach)
                                    StockLevel.OUT -> StatusChip("Rupture", colors.danger)
                                }
                            }
                        }
                    }
                    // Chiffres
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatTile(colors.mint, Icons.Rounded.ShoppingBag, p.soldQuantity.toString(), "Vendus (net des retours)", Modifier.weight(1f))
                        StatTile(colors.peach, Icons.Rounded.Warning, p.lowStockThreshold.toString(), "Seuil d'alerte", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        StatTile(colors.sky, Icons.Rounded.Inventory2, if (p.initial_stock > 0) p.initial_stock.toString() else "—", "Stock de référence", Modifier.weight(1f))
                        if (state.isAdmin) {
                            StatTile(colors.lavender, Icons.Rounded.Payments, formatKmfCompact(p.price * p.quantity), "Valeur du stock (KMF)", Modifier.weight(1f))
                        }
                    }
                    if (!p.description.isNullOrBlank()) {
                        EnkibaCard(Modifier.fillMaxWidth()) {
                            Text("Description", style = MaterialTheme.typography.titleMedium)
                            Text(p.description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = Spacing.sm))
                        }
                    }
                    // Code QR (à imprimer ou scanner en caisse)
                    if (!p.qr_code.isNullOrBlank()) {
                        EnkibaCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                state.qr?.let {
                                    Image(
                                        it.asImageBitmap(),
                                        contentDescription = "Code QR de ${p.name}",
                                        modifier = Modifier.size(180.dp).clip(MaterialTheme.shapes.medium).background(Color.White).padding(8.dp),
                                    )
                                }
                                Text(p.qr_code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    PrimaryButton(
                        "Vendre cet article", onClick = { onSell(p) }, enabled = level != StockLevel.OUT,
                        modifier = Modifier.fillMaxWidth(), trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                    )
                    if (state.isAdmin) {
                        SecondaryButton("Modifier l'article", onClick = { onEdit(p) }, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl))
                    }
                }
            }
        }
    }
}
