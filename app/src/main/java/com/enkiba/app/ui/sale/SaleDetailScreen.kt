package com.enkiba.app.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.enkiba.app.models.SaleItemRow
import com.enkiba.app.ui.components.AmountText
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.InfoGrid
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SectionHeader
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.formatSaleMoment
import com.enkiba.app.ui.components.paymentLabel
import com.enkiba.app.ui.components.softShadow
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

@Composable
fun SaleDetailScreen(
    state: SaleDetailUiState,
    onBack: () -> Unit,
    onInvoice: () -> Unit,
    onReturn: (SaleItemRow) -> Unit,
    onReturnQuantity: (Int) -> Unit,
    onConfirmReturn: () -> Unit,
    onDismissReturn: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val colors = EnkibaTheme.colors
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); onMessageShown() } }
    val d = state.detail

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                title = d?.let { "Vente #${it.sale.shortRef}" } ?: "Vente",
                onBack = onBack,
                actions = {
                    if (d != null) {
                        if (state.pdfBusy) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        else SquareIconButton(Icons.Rounded.PictureAsPdf, "Facture PDF", onClick = onInvoice)
                    }
                },
            )
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                d == null -> EmptyState("Vente introuvable.", actionLabel = "Retour", onAction = onBack)
                else -> Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    val s = d.sale
                    // Récapitulatif (carte vert pastel de la maquette)
                    Column(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(colors.mint.container).padding(Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Montant encaissé", style = MaterialTheme.typography.bodyMedium, color = colors.mint.content)
                                AmountText(s.netTotal, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, currencyColor = colors.mint.content)
                                if (s.refundedAmount > 0) {
                                    Text("Total ${formatKmf(s.totalPrice)} · remboursé ${formatKmf(s.refundedAmount)}",
                                        style = MaterialTheme.typography.bodySmall, color = colors.mint.content)
                                }
                            }
                            if (s.isHasNegotiatedPrice) StatusChip("Négocié", colors.peach)
                        }
                        InfoGrid(
                            listOf(
                                "Paiement" to paymentLabel(s.paymentMethod),
                                "Date" to formatSaleMoment(s.saleDate),
                                "Client" to (d.client?.let { "${it.firstname.orEmpty()} ${it.lastname.orEmpty()}".trim() }?.ifBlank { null } ?: "—"),
                                "Vendeur" to (d.sellerName?.ifBlank { null } ?: "—"),
                            ),
                            labelColor = colors.mint.content,
                        )
                        if (s.sourceQuoteId != null) {
                            Text("Issue d'un devis", style = MaterialTheme.typography.bodySmall, color = colors.mint.content)
                        }
                    }

                    SectionHeader("Articles")
                    d.items.forEach { item -> ItemRow(item, onReturn = { onReturn(item) }) }

                    PrimaryButton("Facture PDF", onClick = onInvoice, enabled = !state.pdfBusy, modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }

    state.returnEditor?.let { ed ->
        AlertDialog(
            onDismissRequest = onDismissReturn,
            title = { Text("Retour : ${ed.item.productName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Text("Quantité retournable : ${ed.max}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        SquareIconButton(Icons.Rounded.Remove, "Diminuer", onClick = { onReturnQuantity(ed.quantity - 1) })
                        Text("${ed.quantity}", style = MaterialTheme.typography.titleLarge)
                        SquareIconButton(Icons.Rounded.Add, "Augmenter", onClick = { onReturnQuantity(ed.quantity + 1) })
                    }
                    Text("À rembourser : ${formatKmf(ed.refund)} KMF", style = MaterialTheme.typography.titleSmall)
                    Text("L'article est remis en stock. La vente d'origine n'est pas modifiée.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = onConfirmReturn, enabled = !ed.busy) { Text("Confirmer le retour") } },
            dismissButton = { TextButton(onClick = onDismissReturn, enabled = !ed.busy) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ItemRow(item: SaleItemRow, onReturn: () -> Unit) {
    val colors = EnkibaTheme.colors
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(MaterialTheme.colorScheme.surface).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.productName.orEmpty(), style = MaterialTheme.typography.titleSmall)
            Text(
                buildAnnotatedString {
                    append("${item.quantity} × ${formatKmf(item.unitPrice)}")
                    if (item.isNegotiated) {
                        append(" ")
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(formatKmf(item.listPrice)) }
                    }
                    if (item.returnedQuantity > 0) {
                        withStyle(SpanStyle(color = colors.peach.content)) { append(" · ${item.returnedQuantity} retourné${if (item.returnedQuantity > 1) "s" else ""}") }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(formatKmf(item.total), style = MaterialTheme.typography.titleSmall)
            if (item.quantity > 0) {
                TextButton(onClick = onReturn) { Text("Retour", color = colors.peach.content, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}
