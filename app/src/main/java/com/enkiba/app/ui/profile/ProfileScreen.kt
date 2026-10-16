package com.enkiba.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.EnkibaTextField
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.displayFirstName
import com.enkiba.app.ui.components.initialsOf
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    val colors = EnkibaTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(title = "Mon profil", onBack = onBack)
            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                return@Column
            }
            Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                // Identité
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    Box(
                        Modifier.size(64.dp).clip(MaterialTheme.shapes.large).background(colors.mint.container),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(initialsOf(state.firstName, state.lastName), style = MaterialTheme.typography.titleLarge, color = colors.link)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            listOf(displayFirstName(state.firstName, state.email), state.lastName.trim()).filterNot { it.isNullOrBlank() }.joinToString(" "),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(state.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusChip(if (state.isAdmin) "Admin" else "Vendeur", colors.mint)
                    }
                }

                EnkibaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        EnkibaTextField(
                            value = state.firstName, onValueChange = onFirstNameChange, label = "Prénom *",
                            isError = state.error != null && state.firstNameMissing, enabled = !state.saving,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        )
                        EnkibaTextField(
                            value = state.lastName, onValueChange = onLastNameChange, label = "Nom", enabled = !state.saving,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        )
                        EnkibaTextField(
                            value = state.phone, onValueChange = onPhoneChange, label = "Téléphone", enabled = !state.saving,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        )
                        Text(
                            "L'e-mail et le rôle sont gérés par l'administrateur de la boutique.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                val feedback = state.error ?: if (state.saved) "Profil enregistré" else null
                if (feedback != null) {
                    Text(
                        feedback,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.error != null) MaterialTheme.colorScheme.error else colors.link,
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

@Preview(showBackground = true)
@Composable
private fun ProfilePreview() {
    EnkibaTheme {
        ProfileScreen(
            ProfileUiState(loading = false, firstName = "", lastName = "", email = "mze.ben.km@gmail.com", isAdmin = true),
            {}, {}, {}, {}, {},
        )
    }
}
