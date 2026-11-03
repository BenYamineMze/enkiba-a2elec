package com.enkiba.app.ui.inventory

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enkiba.app.models.Product
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.EnkibaTextField
import com.enkiba.app.ui.components.ListRow
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ProgressBar
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SearchField
import com.enkiba.app.ui.components.SegmentedTabs
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.formatSaleMoment
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

data class InventoryActions(
    val onBack: () -> Unit = {},
    val onScan: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onTab: (InventoryTab) -> Unit = {},
    val onStart: () -> Unit = {},
    val onEdit: (InventoryLine) -> Unit = {},
    val onCloseEditor: () -> Unit = {},
    val onSave: (Product, Int) -> Unit = { _, _ -> },
    val onAskValidate: () -> Unit = {},
    val onAskCancel: () -> Unit = {},
    val onDismiss: () -> Unit = {},
    val onValidate: () -> Unit = {},
    val onCancel: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@Composable
fun InventoryScreen(state: InventoryUiState, actions: InventoryActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); actions.onMessageShown() } }
    state.editing?.let { CountDialog(it, state.isAdmin, actions) }
    if (state.confirmValidate) ValidateDialog(state.review, actions)
    if (state.confirmCancel) {
        AlertDialog(
            onDismissRequest = actions.onDismiss,
            title = { Text("Annuler l'inventaire ?") },
            text = { Text("Les comptages sont abandonnés et le stock ne change pas.") },
            confirmButton = { TextButton(onClick = actions.onCancel) { Text("Annuler l'inventaire", color = EnkibaTheme.colors.danger.content) } },
            dismissButton = { TextButton(onClick = actions.onDismiss) { Text("Continuer") } },
        )
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            val session = state.session
            ScreenHeader(
                title = "Inventaire",
                subtitle = session?.let { "Ouvert ${formatSaleMoment(it.createdAt)}" },
                onBack = actions.onBack,
                actions = {
                    if (session != null) SquareIconButton(Icons.Rounded.QrCodeScanner, "Scanner un article", onClick = actions.onScan, filled = true)
                },
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                session == null -> NoSession(state.isAdmin, actions)
                else -> OpenSession(state, actions, Modifier.weight(1f))
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }
}

@Composable
private fun NoSession(isAdmin: Boolean, actions: InventoryActions) {
    Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        EnkibaCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Aucun inventaire en cours", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (isAdmin) "Ouvrez un inventaire : chacun scanne les articles et saisit la quantité comptée, même hors ligne. " +
                        "Vous validez ensuite les écarts, qui corrigent le stock, et un rapport PDF est créé."
                    else "L'admin doit ouvrir un inventaire pour que vous puissiez compter les articles.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isAdmin) PrimaryButton("Démarrer un inventaire", onClick = actions.onStart, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OpenSession(state: InventoryUiState, actions: InventoryActions, modifier: Modifier) {
    val colors = EnkibaTheme.colors
    val total = state.review.lines.size
    val done = state.review.counted.size
    Column(modifier) {
        EnkibaCard(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("$done / $total articles comptés", style = MaterialTheme.typography.titleMedium)
                ProgressBar(progress = if (total == 0) 0f else done.toFloat() / total, modifier = Modifier.fillMaxWidth())
                if (state.session?.syncStatus == "pending") {
                    Text("Pas encore envoyé : les autres téléphones le verront après la synchronisation.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SearchField(state.query, actions.onQuery, "Nom, catégorie ou code", Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.md))
        SegmentedTabs(
            options = state.tabs.map { "${it.label} · ${state.count(it)}" },
            selectedIndex = state.tabs.indexOf(state.tab).coerceAtLeast(0),
            onSelect = { actions.onTab(state.tabs[it]) },
            modifier = Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (state.visible.isEmpty()) {
                item {
                    EmptyState(
                        when (state.tab) {
                            InventoryTab.TO_COUNT -> if (state.query.isBlank()) "Tous les articles sont comptés." else "Aucun article trouvé."
                            InventoryTab.COUNTED -> "Aucun article compté pour l'instant."
                            InventoryTab.GAPS -> "Aucun écart : le compté correspond au stock attendu."
                        },
                    )
                }
            }
            items(state.visible, key = { it.product.id }) { line ->
                val diff = line.diff
                ListRow(
                    title = line.product.name.orEmpty(),
                    subtitle = line.product.category?.ifBlank { null },
                    onClick = { actions.onEdit(line) },
                    trailing = {
                        line.count?.let { c ->
                            Text("${c.counted}", style = MaterialTheme.typography.titleMedium)
                            // Écart visible seulement par l'admin (les vendeurs comptent à l'aveugle)
                            if (state.isAdmin && diff != null && diff != 0) {
                                StatusChip(if (diff > 0) "+$diff" else "$diff", if (diff > 0) colors.sky else colors.peach)
                            }
                        }
                    },
                )
            }
        }
        if (state.isAdmin) {
            Column(Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (state.busy) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
                } else {
                    PrimaryButton("Valider l'inventaire", onClick = actions.onAskValidate, modifier = Modifier.fillMaxWidth(), enabled = done > 0)
                    TextButton(onClick = actions.onAskCancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Annuler l'inventaire", color = colors.danger.content)
                    }
                }
            }
        }
    }
}

/** Saisie de la quantité comptée. Le stock attendu n'est pas montré : comptage à l'aveugle. */
@Composable
private fun CountDialog(line: InventoryLine, isAdmin: Boolean, actions: InventoryActions) {
    var text by remember(line.product.id) { mutableStateOf(line.count?.counted?.toString().orEmpty()) }
    val value = InventoryRules.parseCount(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(line.product.id) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = actions.onCloseEditor,
        title = { Text(line.product.name.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                EnkibaTextField(
                    text, { v -> text = v.filter(Char::isDigit).take(6) }, "Quantité comptée",
                    modifier = Modifier.focusRequester(focus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { value?.let { actions.onSave(line.product, it) } }),
                )
                line.count?.let {
                    Text("Déjà compté : ${it.counted} (${formatSaleMoment(it.countedAt)}). La nouvelle saisie remplace l'ancienne.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isAdmin) {
                    Text("Stock attendu : ${line.product.quantity}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { value?.let { actions.onSave(line.product, it) } }, enabled = value != null) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = actions.onCloseEditor) { Text("Annuler") } },
    )
}

@Composable
private fun ValidateDialog(r: InventoryReview, actions: InventoryActions) {
    AlertDialog(
        onDismissRequest = actions.onDismiss,
        title = { Text("Valider l'inventaire ?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SummaryLine("Articles comptés", "${r.counted.size}")
                SummaryLine("Écarts", "${r.gaps.size}")
                SummaryLine("Manquants", "${r.missingUnits} u. · − ${formatKmf(r.missingValue)} KMF")
                SummaryLine("Surplus", "${r.surplusUnits} u. · + ${formatKmf(r.surplusValue)} KMF")
                if (r.uncounted.isNotEmpty()) SummaryLine("Non comptés (stock inchangé)", "${r.uncounted.size}")
                Text(
                    "Le stock sera corrigé pour chaque écart et un rapport PDF sera créé. Connexion internet nécessaire.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
        },
        confirmButton = { TextButton(onClick = actions.onValidate) { Text("Valider") } },
        dismissButton = { TextButton(onClick = actions.onDismiss) { Text("Plus tard") } },
    )
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
