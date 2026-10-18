package com.enkiba.app.ui.quotes

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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
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
import com.enkiba.app.models.Quote
import com.enkiba.app.models.SaleHeader
import com.enkiba.app.ui.components.AmountText
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.InfoGrid
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SecondaryButton
import com.enkiba.app.ui.components.SectionHeader
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.formatSaleMoment
import com.enkiba.app.ui.components.softShadow
import com.enkiba.app.ui.sale.Checkout
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing
import com.enkiba.app.ui.theme.Tile

data class QuoteDetailActions(
    val onBack: () -> Unit = {},
    val onPdf: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onAccept: () -> Unit = {},
    val onReject: () -> Unit = {},
    val onAskDelete: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onCancelDelete: () -> Unit = {},
    val onStartConversion: () -> Unit = {},
    val onContinueConversion: () -> Unit = {},
    val onConvert: (String) -> Unit = {},
    val onCancelConversion: () -> Unit = {},
    val onOpenSale: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** Tuile de couleur d'un statut de devis (liste et détail). */
@Composable
fun quoteTile(q: Quote): Tile {
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
fun QuoteDetailScreen(state: QuoteDetailUiState, actions: QuoteDetailActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); actions.onMessageShown() } }
    val d = state.detail

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                title = d?.let { "Devis ${it.quote.shortRef}" } ?: "Devis",
                onBack = actions.onBack,
                actions = {
                    if (d != null) {
                        if (state.pdfBusy) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        else Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            SquareIconButton(Icons.Rounded.Share, "Partager le devis", onClick = actions.onShare)
                            SquareIconButton(Icons.Rounded.PictureAsPdf, "PDF du devis", onClick = actions.onPdf)
                        }
                    }
                },
            )
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                d == null -> EmptyState("Ce devis n'existe plus.", actionLabel = "Retour", onAction = actions.onBack)
                else -> Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    val q = d.quote
                    val tile = quoteTile(q)
                    Column(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge).background(tile.container).padding(Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Total du devis", style = MaterialTheme.typography.bodyMedium, color = tile.content)
                                AmountText(q.totalPrice, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface, currencyColor = tile.content)
                            }
                            StatusChip(q.statusLabel, tile)
                        }
                        InfoGrid(
                            listOf(
                                "Client" to (d.client?.let { "${it.firstname.orEmpty()} ${it.lastname.orEmpty()}".trim() }?.ifBlank { null } ?: "—"),
                                "Établi par" to (d.sellerName?.ifBlank { null } ?: "—"),
                                "Date" to formatSaleMoment(q.createdAt),
                                (if (q.isExpired) "Expiré le" else "Valable jusqu'au") to (q.validUntil ?: "—"),
                            ),
                            labelColor = tile.content,
                        )
                        d.client?.phone?.takeIf { it.isNotBlank() }?.let {
                            Text("Téléphone du client : $it", style = MaterialTheme.typography.bodySmall, color = tile.content)
                        }
                    }

                    q.convertedSaleId?.let { saleId ->
                        SecondaryButton("Voir la vente #${SaleHeader.shortRef(saleId)}", onClick = { actions.onOpenSale(saleId) }, modifier = Modifier.fillMaxWidth())
                    }

                    SectionHeader("Articles")
                    d.items.forEach { r ->
                        val shape = MaterialTheme.shapes.medium
                        Row(
                            Modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(MaterialTheme.colorScheme.surface).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(r.productName.orEmpty(), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    buildAnnotatedString {
                                        append("${r.quantity} × ${formatKmf(r.unitPrice)}")
                                        if (r.isNegotiated) {
                                            append(" ")
                                            withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(formatKmf(r.listPrice)) }
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(formatKmf(r.total), style = MaterialTheme.typography.titleSmall)
                        }
                    }

                    PrimaryButton("Envoyer par WhatsApp", onClick = actions.onShare, enabled = !state.pdfBusy,
                        modifier = Modifier.fillMaxWidth())
                    if (state.canConvert) {
                        PrimaryButton("Convertir en vente", onClick = actions.onStartConversion, modifier = Modifier.fillMaxWidth(),
                            trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward)
                    }
                    if (state.canChangeStatus) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            SecondaryButton("Accepté", onClick = actions.onAccept, enabled = q.status != Quote.STATUS_ACCEPTED, modifier = Modifier.weight(1f))
                            SecondaryButton("Refusé", onClick = actions.onReject, enabled = q.status != Quote.STATUS_REJECTED, modifier = Modifier.weight(1f))
                        }
                        TextButton(onClick = actions.onAskDelete, modifier = Modifier.fillMaxWidth()) {
                            Text("Supprimer le devis", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Box(Modifier.padding(bottom = Spacing.xl))
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }

    if (state.confirmDelete && d != null) {
        AlertDialog(
            onDismissRequest = actions.onCancelDelete,
            title = { Text("Supprimer le devis ${d.quote.shortRef} ?") },
            text = { Text("Le devis disparaîtra de la liste sur tous les téléphones.") },
            confirmButton = { TextButton(onClick = actions.onConfirmDelete) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = actions.onCancelDelete) { Text("Annuler") } },
        )
    }

    when (val step = state.conversion) {
        is ConversionStep.Warnings -> AlertDialog(
            onDismissRequest = actions.onCancelConversion,
            title = { Text("Convertir quand même ?") },
            text = { Text(step.warnings.joinToString("\n") { "• $it" }) },
            confirmButton = { TextButton(onClick = actions.onContinueConversion) { Text("Continuer") } },
            dismissButton = { TextButton(onClick = actions.onCancelConversion) { Text("Annuler") } },
        )
        ConversionStep.ChoosePayment -> AlertDialog(
            onDismissRequest = actions.onCancelConversion,
            title = { Text("Mode de paiement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PrimaryButton("Espèces", onClick = { actions.onConvert(Checkout.PAYMENT_CASH) }, modifier = Modifier.fillMaxWidth())
                    SecondaryButton("Virement", onClick = { actions.onConvert(Checkout.PAYMENT_TRANSFER) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = actions.onCancelConversion) { Text("Annuler") } },
        )
        ConversionStep.Converting -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Conversion en cours…") },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) } },
            confirmButton = {},
        )
        null -> Unit
    }
}
