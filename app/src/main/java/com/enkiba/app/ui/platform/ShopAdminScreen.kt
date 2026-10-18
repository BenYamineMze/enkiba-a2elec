package com.enkiba.app.ui.platform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.EnkibaTextField
import com.enkiba.app.ui.components.InfoGrid
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SecondaryButton
import com.enkiba.app.ui.components.SectionHeader
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.formatKmf
import com.enkiba.app.ui.components.formatSaleMoment
import com.enkiba.app.ui.shop.ShopForm
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

/** Fiche d'une boutique pour le super-admin : indicateurs, coordonnées, membres, suspension. */
@Composable
fun ShopAdminScreen(
    state: ShopAdminUiState,
    onBack: () -> Unit,
    onChange: ((ShopForm) -> ShopForm) -> Unit,
    onSave: () -> Unit,
    onAskSuspend: () -> Unit,
    onConfirmSuspend: () -> Unit,
    onCancelSuspend: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val colors = EnkibaTheme.colors
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); onMessageShown() } }
    val shop = state.shop
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(title = shop?.name ?: "Boutique", subtitle = if (shop?.isSuspended == true) "Suspendue" else null, onBack = onBack)
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                shop == null -> EmptyState(state.error ?: "Boutique introuvable.", actionLabel = "Retour", onAction = onBack)
                else -> Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    val tile = if (shop.isSuspended) colors.neutral else colors.mint
                    Column(
                        Modifier.fillMaxWidth().background(tile.container, MaterialTheme.shapes.extraLarge).padding(Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ce mois-ci", style = MaterialTheme.typography.bodyMedium, color = tile.content, modifier = Modifier.weight(1f))
                            StatusChip(if (shop.isSuspended) "Suspendue" else "Active", tile)
                        }
                        Text("${formatKmf(shop.revenueMonth)} KMF", style = MaterialTheme.typography.headlineSmall)
                        InfoGrid(
                            listOf(
                                "Ventes du mois" to shop.salesMonth.toString(),
                                "Articles" to shop.products.toString(),
                                "Membres" to "${shop.members} (${shop.admins} admin${if (shop.admins > 1) "s" else ""})",
                                "Dernière vente" to (shop.lastSaleAt?.let { formatSaleMoment(it) } ?: "—"),
                            ),
                            labelColor = tile.content,
                        )
                    }

                    EnkibaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Text("Coordonnées", style = MaterialTheme.typography.titleSmall)
                            val f = state.form
                            EnkibaTextField(f.name, { v -> onChange { it.copy(name = v.take(ShopForm.MAX_NAME)) } }, "Nom de la boutique *",
                                enabled = !state.saving, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                            EnkibaTextField(f.address, { v -> onChange { it.copy(address = v.take(ShopForm.MAX_ADDRESS)) } }, "Adresse",
                                enabled = !state.saving, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                            EnkibaTextField(f.phone, { v -> onChange { it.copy(phone = ShopForm.cleanPhone(v)) } }, "Téléphone",
                                enabled = !state.saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                            EnkibaTextField(f.email, { v -> onChange { it.copy(email = v.trim()) } }, "E-mail",
                                enabled = !state.saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done))
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                            PrimaryButton("Enregistrer", onClick = onSave, enabled = state.edited && !state.saving, modifier = Modifier.fillMaxWidth())
                        }
                    }

                    SectionHeader("Membres (${state.members.size})")
                    state.members.forEach { m ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Column(Modifier.weight(1f)) {
                                Text("${m.username.orEmpty()} ${m.lastname.orEmpty()}".trim().ifEmpty { m.email.orEmpty() }, style = MaterialTheme.typography.titleSmall)
                                Text(listOf(m.email.orEmpty(), m.telephone.orEmpty()).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusChip(if (m.role == "admin") "Admin" else "Vendeur", if (m.role == "admin") colors.mint else colors.sky)
                            if (!m.isActive) StatusChip("Désactivé", colors.neutral)
                        }
                    }

                    if (shop.isSuspended) PrimaryButton("Réactiver la boutique", onClick = onAskSuspend, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                    else SecondaryButton("Suspendre la boutique", onClick = onAskSuspend, enabled = !state.saving, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Une boutique suspendue garde toutes ses données, mais ses membres ne peuvent plus se connecter ni synchroniser.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xl),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }

    if (state.confirmSuspend && shop != null) {
        val suspend = !shop.isSuspended
        AlertDialog(
            onDismissRequest = onCancelSuspend,
            title = { Text(if (suspend) "Suspendre ${shop.name} ?" else "Réactiver ${shop.name} ?") },
            text = {
                Text(
                    if (suspend) "Ses ${shop.members} membre(s) seront déconnectés à leur prochaine synchronisation et ne pourront plus se connecter. Ses données restent intactes."
                    else "Ses membres pourront de nouveau se connecter et synchroniser."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmSuspend) {
                    Text(if (suspend) "Suspendre" else "Réactiver", color = if (suspend) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = { TextButton(onClick = onCancelSuspend) { Text("Annuler") } },
        )
    }
}
