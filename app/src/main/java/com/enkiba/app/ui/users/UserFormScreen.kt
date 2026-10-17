package com.enkiba.app.ui.users

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
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.EnkibaTextField
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.theme.Spacing

@Composable
fun UserFormScreen(
    state: UserFormUiState,
    onChange: ((UserForm) -> UserForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val f = state.form
    val enabled = !state.saving
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(
                title = if (f.isNew) "Nouveau vendeur" else "Modifier le vendeur",
                subtitle = if (f.isNew) null else f.email,
                onBack = onBack,
            )
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                state.loadError != null -> EmptyState(state.loadError, actionLabel = "Retour", onAction = onBack)
                else -> Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    EnkibaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Text("Identité", style = MaterialTheme.typography.titleSmall)
                            EnkibaTextField(
                                value = f.firstName, onValueChange = { v -> onChange { it.copy(firstName = v) } }, label = "Prénom *", enabled = enabled,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            )
                            EnkibaTextField(
                                value = f.lastName, onValueChange = { v -> onChange { it.copy(lastName = v) } }, label = "Nom *", enabled = enabled,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            )
                            EnkibaTextField(
                                value = f.phone, onValueChange = { v -> onChange { it.copy(phone = v) } }, label = "Téléphone *", enabled = enabled,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                            )
                        }
                    }
                    EnkibaCard(Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            Text("Connexion", style = MaterialTheme.typography.titleSmall)
                            // L'e-mail sert d'identifiant : il ne change plus après la création
                            EnkibaTextField(
                                value = f.email, onValueChange = { v -> onChange { it.copy(email = v) } }, label = if (f.isNew) "E-mail *" else "E-mail",
                                enabled = enabled && f.isNew,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            )
                            EnkibaTextField(
                                value = f.password, onValueChange = { v -> onChange { it.copy(password = v) } },
                                label = if (f.isNew) "Mot de passe *" else "Nouveau mot de passe", isPassword = true, enabled = enabled,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            )
                            Text(
                                if (f.isNew) "Au moins ${UserForm.MIN_PASSWORD} caractères. Communiquez-le au vendeur : il pourra se connecter tout de suite."
                                else "Laissez vide pour garder le mot de passe actuel.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    if (state.saving) {
                        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        PrimaryButton(if (f.isNew) "Créer le vendeur" else "Enregistrer", onClick = onSave, modifier = Modifier.fillMaxWidth())
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
}
