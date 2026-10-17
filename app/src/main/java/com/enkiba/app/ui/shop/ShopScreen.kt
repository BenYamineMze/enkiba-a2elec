package com.enkiba.app.ui.shop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.EnkibaTextField
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

/** « Ma boutique » (admin) : nom et coordonnées imprimés sur les factures et les devis. */
@Composable
fun ShopScreen(
    state: ShopUiState,
    onChange: ((ShopForm) -> ShopForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val f = state.form
    val enabled = !state.saving
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(title = "Ma boutique", subtitle = "Factures et devis", onBack = onBack)
            Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                EnkibaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        EnkibaTextField(
                            value = f.name, onValueChange = { v -> onChange { it.copy(name = v.take(ShopForm.MAX_NAME)) } },
                            label = "Nom de la boutique *", enabled = enabled, isError = state.error != null && f.name.isBlank(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        )
                        EnkibaTextField(
                            value = f.address, onValueChange = { v -> onChange { it.copy(address = v.take(ShopForm.MAX_ADDRESS)) } },
                            label = "Adresse", enabled = enabled,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        )
                        EnkibaTextField(
                            value = f.phone, onValueChange = { v -> onChange { it.copy(phone = ShopForm.cleanPhone(v)) } },
                            label = "Téléphone", enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        )
                        EnkibaTextField(
                            value = f.email, onValueChange = { v -> onChange { it.copy(email = v.trim()) } },
                            label = "E-mail", enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        )
                        Text(
                            "Ces informations apparaissent en haut et en bas des factures et des devis. " +
                                "Les champs vides ne sont pas imprimés.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val feedback = state.error ?: if (state.saved) "Coordonnées enregistrées" else null
                if (feedback != null) {
                    Text(
                        feedback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else EnkibaTheme.colors.link,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (state.saving) {
                    Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    PrimaryButton("Enregistrer", onClick = onSave, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    "Une connexion internet est nécessaire pour enregistrer.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.xl),
                )
            }
        }
    }
}
