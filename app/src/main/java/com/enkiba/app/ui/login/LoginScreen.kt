package com.enkiba.app.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enkiba.app.R
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.EnkibaTextField
import com.enkiba.app.ui.components.PrimaryButton
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

/** Écran de connexion (sans état : piloté par LoginViewModel). */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val focus = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(EnkibaTheme.colors.mint.container),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.enkiba_logo), contentDescription = "Enkiba", modifier = Modifier.size(72.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bienvenue", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Connectez-vous à votre boutique",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            EnkibaCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    EnkibaTextField(
                        value = state.email,
                        onValueChange = onEmailChange,
                        label = "E-mail",
                        enabled = !state.loading,
                        isError = state.error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                    EnkibaTextField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = "Mot de passe",
                        isPassword = true,
                        enabled = !state.loading,
                        isError = state.error != null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); onLogin() }),
                    )
                    if (state.error != null) {
                        Text(
                            state.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    if (state.loading) {
                        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        PrimaryButton("Se connecter", onClick = { focus.clearFocus(); onLogin() }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Text(
                "Une connexion internet est nécessaire pour se connecter. Ensuite, l'application fonctionne aussi hors ligne.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginPreview() {
    EnkibaTheme {
        LoginScreen(LoginUiState(email = "vendeur@exemple.km", error = "Identifiants incorrects"), {}, {}, {})
    }
}
