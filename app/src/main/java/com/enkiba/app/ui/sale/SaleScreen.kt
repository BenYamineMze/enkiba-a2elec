package com.enkiba.app.ui.sale

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enkiba.app.models.Product
import com.enkiba.app.ui.components.AmountText
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.EnkibaTextField
import com.enkiba.app.ui.components.FilterPill
import com.enkiba.app.ui.components.IconBadge
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SearchField
import com.enkiba.app.ui.components.SegmentedTabs
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.softShadow
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing
import com.enkiba.app.ui.theme.Tile
import kotlin.math.abs

/** Actions de l'écran de vente (branchées par SaleActivity). */
data class SaleActions(
    val onBack: () -> Unit = {},
    val onHistory: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onCategorySelect: (String?) -> Unit = {},
    val onProductClick: (Product) -> Unit = {},
    val onQuickAdd: (Product) -> Unit = {},
    val onEditorChange: ((ItemEditor) -> ItemEditor) -> Unit = {},
    val onEditorConfirm: (Boolean) -> Unit = {},
    val onEditorDismiss: () -> Unit = {},
    val onOpenCheckout: (CheckoutMode) -> Unit = {},
    val onLineQuantity: (CartLine, Int) -> Unit = { _, _ -> },
    val onCheckoutChange: ((Checkout) -> Checkout) -> Unit = {},
    val onCheckoutSubmit: () -> Unit = {},
    val onCheckoutDismiss: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    /** Envoi par WhatsApp de la facture ou du devis qui vient d'être créé. */
    val onShareOffer: (SaleEvent) -> Unit = {},
    val onShareOfferDismissed: () -> Unit = {},
)

@Composable
fun SaleScreen(state: SaleUiState, actions: SaleActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            val offer = state.shareOffer
            actions.onMessageShown()
            if (offer == null) {
                snackbar.showSnackbar(msg)
            } else {
                // L'offre ne vaut que pour ce message : retirée tout de suite de l'état
                actions.onShareOfferDismissed()
                // Reste affiché : le PDF s'ouvre aussitôt, le message doit encore être là au retour
                val result = snackbar.showSnackbar(msg, actionLabel = "WhatsApp", withDismissAction = true, duration = SnackbarDuration.Indefinite)
                if (result == SnackbarResult.ActionPerformed) actions.onShareOffer(offer)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Nouvelle vente",
                onBack = actions.onBack,
                actions = { SquareIconButton(Icons.Rounded.History, "Historique des ventes", onClick = actions.onHistory) },
            )
            SearchField(
                value = state.query,
                onValueChange = actions.onQueryChange,
                placeholder = "Rechercher un article",
                modifier = Modifier.padding(horizontal = Spacing.screen),
                trailing = { SquareIconButton(Icons.Rounded.QrCodeScanner, "Scanner un code QR", onClick = actions.onScan, filled = true, size = 38.dp) },
            )
            if (state.categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item { FilterPill("Tout", selected = state.category == null, onClick = { actions.onCategorySelect(null) }) }
                    items(state.categories) { c ->
                        FilterPill(c, selected = state.category == c, onClick = { actions.onCategorySelect(c) })
                    }
                }
            }

            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.visibleProducts.isEmpty() -> EmptyState(
                    if (state.products.isEmpty()) "Aucun article dans le catalogue." else "Aucun article ne correspond à la recherche.",
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    items(state.visibleProducts, key = { it.id }) { p ->
                        ProductCard(p, inCart = state.inCart(p.id), onClick = { actions.onProductClick(p) }, onAdd = { actions.onQuickAdd(p) })
                    }
                }
            }
        }

        if (state.cart.isNotEmpty()) {
            CartBar(
                count = state.cartCount,
                total = state.cartTotal,
                onQuote = { actions.onOpenCheckout(CheckoutMode.QUOTE) },
                onCheckout = { actions.onOpenCheckout(CheckoutMode.SALE) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = Spacing.lg, vertical = 18.dp),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (state.cart.isEmpty()) 16.dp else 110.dp))
    }

    state.editor?.let { ItemEditorDialog(it, actions) }
    state.checkout?.let { CheckoutSheet(state, it, actions) }
}

// ----------------------------------------------------------------------
// Carte article
// ----------------------------------------------------------------------

/** Couleur et icône d'une catégorie (stables d'un lancement à l'autre). */
@Composable
private fun categoryStyle(category: String?): Pair<Tile, ImageVector> {
    val colors = EnkibaTheme.colors
    val tiles = listOf(colors.mint, colors.sand, colors.sky, colors.peach, colors.lavender)
    val c = category.orEmpty().lowercase()
    val icon = when {
        "câbl" in c || "cabl" in c || "fil" in c -> Icons.Rounded.Cable
        "éclair" in c || "eclair" in c || "lamp" in c || "ampoul" in c -> Icons.Rounded.Lightbulb
        "protect" in c || "disjonct" in c -> Icons.Rounded.Shield
        else -> Icons.Rounded.ElectricalServices
    }
    return tiles[abs(c.hashCode()) % tiles.size] to icon
}

@Composable
private fun ProductCard(product: Product, inCart: Int, onClick: () -> Unit, onAdd: () -> Unit) {
    val colors = EnkibaTheme.colors
    val (tile, icon) = categoryStyle(product.category)
    val outOfStock = product.quantity <= 0
    val low = !outOfStock && product.quantity <= product.lowStockThreshold
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .softShadow(shape, 6.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = !outOfStock, role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(72.dp).clip(MaterialTheme.shapes.medium).background(tile.container),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = tile.content, modifier = Modifier.size(30.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(product.name.orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    outOfStock -> "Rupture de stock"
                    low -> "Stock faible : ${product.quantity}"
                    else -> "Stock : ${product.quantity}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    outOfStock -> colors.danger.content
                    low -> colors.peach.content
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatKmf(product.price), style = MaterialTheme.typography.titleSmall)
            if (inCart > 0) {
                Text(
                    "× $inCart",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !outOfStock, role = Role.Button, onClick = onAdd)
                        .semantics { contentDescription = "${product.name} : $inCart dans le panier, ajouter un" }
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            } else {
                IconButton(
                    onClick = onAdd,
                    enabled = !outOfStock,
                    modifier = Modifier.size(Spacing.touch).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.primaryContainer),
                ) { Icon(Icons.Rounded.Add, contentDescription = "Ajouter ${product.name} au panier", tint = colors.link) }
            }
        }
    }
}

// ----------------------------------------------------------------------
// Barre du panier
// ----------------------------------------------------------------------

@Composable
private fun CartBar(count: Int, total: Double, onQuote: () -> Unit, onCheckout: () -> Unit, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.extraLarge
    Row(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(shape, 16.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(start = 20.dp, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Panier · $count article${if (count > 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
            )
            AmountText(
                total,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                currencyColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f),
            )
        }
        TextButton(
            onClick = onQuote,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text("Devis", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface) }
        PrimaryButton("Encaisser", onClick = onCheckout, trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward)
    }
}

// ----------------------------------------------------------------------
// Saisie d'un article (quantité, prix négocié)
// ----------------------------------------------------------------------

@Composable
private fun ItemEditorDialog(editor: ItemEditor, actions: SaleActions) {
    val p = editor.product
    if (editor.confirmDiscountPercent != null) {
        AlertDialog(
            onDismissRequest = { actions.onEditorChange { it } },
            title = { Text("Remise importante") },
            text = { Text("Remise de ${editor.confirmDiscountPercent} % sur ${p.name} (prix catalogue ${formatKmf(p.price)} KMF). Confirmer ?") },
            confirmButton = { TextButton(onClick = { actions.onEditorConfirm(true) }) { Text("Confirmer") } },
            dismissButton = { TextButton(onClick = { actions.onEditorChange { it } }) { Text("Modifier") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = actions.onEditorDismiss,
        title = { Text(p.name.orEmpty()) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    "Stock : ${p.quantity} · prix catalogue ${formatKmf(p.price)} KMF",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                QuantityStepper(
                    value = editor.quantityText,
                    onValueChange = { v -> actions.onEditorChange { it.copy(quantityText = v.filter(Char::isDigit)) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Prix négocié", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = editor.negotiated, onCheckedChange = { on -> actions.onEditorChange { it.copy(negotiated = on) } })
                }
                if (editor.negotiated) {
                    EnkibaTextField(
                        value = editor.priceText,
                        onValueChange = { v -> actions.onEditorChange { it.copy(priceText = v.filter { c -> c.isDigit() || c == ',' || c == '.' }) } },
                        label = "Prix unitaire (KMF)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Text("Total ligne : ${formatKmf(editor.lineTotal)} KMF", style = MaterialTheme.typography.titleSmall)
                editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { TextButton(onClick = { actions.onEditorConfirm(false) }) { Text("Ajouter au panier") } },
        dismissButton = { TextButton(onClick = actions.onEditorDismiss) { Text("Annuler") } },
    )
}

/** Quantité avec boutons − / + et saisie directe. */
@Composable
private fun QuantityStepper(value: String, onValueChange: (String) -> Unit) {
    val current = value.toIntOrNull() ?: 0
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SquareIconButton(Icons.Rounded.Remove, "Diminuer la quantité", onClick = { onValueChange((current - 1).coerceAtLeast(1).toString()) })
        EnkibaTextField(
            value = value,
            onValueChange = onValueChange,
            label = "Quantité",
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        SquareIconButton(Icons.Rounded.Add, "Augmenter la quantité", onClick = { onValueChange((current + 1).toString()) })
    }
}

// ----------------------------------------------------------------------
// Validation : panier, paiement, client (vente ou devis)
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CheckoutSheet(state: SaleUiState, checkout: Checkout, actions: SaleActions) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = EnkibaTheme.colors
    ModalBottomSheet(onDismissRequest = actions.onCheckoutDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .navigationBarsPadding()
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(if (checkout.mode == CheckoutMode.SALE) "Encaisser la vente" else "Établir un devis", style = MaterialTheme.typography.headlineSmall)

            // Lignes du panier
            state.cart.forEach { line ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(line.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatKmf(line.unitPrice)} KMF" + if (line.negotiated) " · négocié (${formatKmf(line.listPrice)})" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (line.negotiated) colors.peach.content else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { actions.onLineQuantity(line, line.quantity - 1) }) {
                        Icon(if (line.quantity > 1) Icons.Rounded.Remove else Icons.Rounded.Delete,
                            contentDescription = if (line.quantity > 1) "Retirer un ${line.name}" else "Supprimer ${line.name} du panier")
                    }
                    Text("${line.quantity}", style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { actions.onLineQuantity(line, line.quantity + 1) }) {
                        Icon(Icons.Rounded.Add, contentDescription = "Ajouter un ${line.name}")
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AmountText(state.cartTotal)
            }

            if (checkout.mode == CheckoutMode.SALE) {
                Text("Paiement", style = MaterialTheme.typography.titleMedium)
                SegmentedTabs(
                    options = listOf("Espèces", "Virement"),
                    selectedIndex = if (checkout.paymentMethod == Checkout.PAYMENT_TRANSFER) 1 else 0,
                    onSelect = { i -> actions.onCheckoutChange { it.copy(paymentMethod = if (i == 1) Checkout.PAYMENT_TRANSFER else Checkout.PAYMENT_CASH) } },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Facture avec client", style = MaterialTheme.typography.bodyLarge)
                        Text("Le PDF s'ouvre après l'encaissement", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = checkout.withInvoice, onCheckedChange = { on -> actions.onCheckoutChange { it.copy(withInvoice = on) } })
                }
            } else {
                StatusChip("Valable 30 jours", colors.sky)
            }

            if (checkout.clientRequired) {
                Text("Client", style = MaterialTheme.typography.titleMedium)
                val c = checkout.client
                EnkibaTextField(c.lastname, { v -> actions.onCheckoutChange { it.copy(client = it.client.copy(lastname = v)) } }, "Nom *",
                    isError = checkout.error != null && c.isEmpty)
                EnkibaTextField(c.firstname, { v -> actions.onCheckoutChange { it.copy(client = it.client.copy(firstname = v)) } }, "Prénom")
                EnkibaTextField(c.phone, { v -> actions.onCheckoutChange { it.copy(client = it.client.copy(phone = v)) } }, "Téléphone",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                EnkibaTextField(c.address, { v -> actions.onCheckoutChange { it.copy(client = it.client.copy(address = v)) } }, "Adresse")
                EnkibaTextField(c.email, { v -> actions.onCheckoutChange { it.copy(client = it.client.copy(email = v)) } }, "E-mail",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            }

            checkout.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            if (checkout.busy) {
                Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                PrimaryButton(
                    text = if (checkout.mode == CheckoutMode.SALE) "Encaisser ${formatKmf(state.cartTotal)} KMF" else "Enregistrer le devis",
                    onClick = actions.onCheckoutSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
