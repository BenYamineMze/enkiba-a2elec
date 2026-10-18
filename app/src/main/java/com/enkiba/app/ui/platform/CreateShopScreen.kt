package com.enkiba.app.ui.platform

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
import com.enkiba.app.ui.shop.ShopForm
import com.enkiba.app.ui.theme.Spacing
import com.enkiba.app.ui.users.UserForm

/** Nouvelle boutique et son administrateur (super-admin, en ligne). */
@Composable
fun CreateShopScreen(
    state: CreateShopUiState,
    onShop: ((ShopForm) -> ShopForm) -> Unit,
    onAdmin: ((UserForm) -> UserForm) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val enabled = !state.saving
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(title = "Nouvelle boutique", onBack = onBack)
            Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                EnkibaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text("Boutique", style = MaterialTheme.typography.titleSmall)
                        val s = state.shop
                        EnkibaTextField(s.name, { v -> onShop { it.copy(name = v.take(ShopForm.MAX_NAME)) } }, "Nom de la boutique *", enabled = enabled, keyboardOptions = words)
                        EnkibaTextField(s.address, { v -> onShop { it.copy(address = v.take(ShopForm.MAX_ADDRESS)) } }, "Adresse", enabled = enabled,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next))
                        EnkibaTextField(s.phone, { v -> onShop { it.copy(phone = ShopForm.cleanPhone(v)) } }, "Téléphone", enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                        EnkibaTextField(s.email, { v -> onShop { it.copy(email = v.trim()) } }, "E-mail de la boutique", enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    }
                }
                EnkibaCard(Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Text("Administrateur de la boutique", style = MaterialTheme.typography.titleSmall)
                        val a = state.admin
                        EnkibaTextField(a.firstName, { v -> onAdmin { it.copy(firstName = v) } }, "Prénom *", enabled = enabled, keyboardOptions = words)
                        EnkibaTextField(a.lastName, { v -> onAdmin { it.copy(lastName = v) } }, "Nom *", enabled = enabled, keyboardOptions = words)
                        EnkibaTextField(a.phone, { v -> onAdmin { it.copy(phone = v) } }, "Téléphone *", enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next))
                        EnkibaTextField(a.email, { v -> onAdmin { it.copy(email = v) } }, "E-mail de connexion *", enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                        EnkibaTextField(a.password, { v -> onAdmin { it.copy(password = v) } }, "Mot de passe provisoire *", isPassword = true, enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
                        Text("Au moins ${UserForm.MIN_PASSWORD} caractères. Communiquez-le à l'administrateur : il pourra se connecter tout de suite.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (state.saving) Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else PrimaryButton("Créer la boutique", onClick = onSave, modifier = Modifier.fillMaxWidth())
                Text("Une connexion internet est nécessaire.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = Spacing.xl))
            }
        }
    }
}
