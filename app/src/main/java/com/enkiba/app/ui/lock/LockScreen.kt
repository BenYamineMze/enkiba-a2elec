package com.enkiba.app.ui.lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enkiba.app.R
import com.enkiba.app.ui.components.displayFirstName
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

@Composable
fun LockScreen(
    state: LockUiState,
    firstName: String?,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onBiometric: (() -> Unit)?,
    onForgot: () -> Unit,
) {
    var confirmForgot by rememberSaveable { mutableStateOf(false) }
    if (confirmForgot) {
        AlertDialog(
            onDismissRequest = { confirmForgot = false },
            title = { Text("Code oublié ?") },
            text = {
                Text(
                    "Vous allez être déconnecté. Reconnectez-vous avec votre e-mail et votre mot de passe, " +
                        "puis choisissez un nouveau code. Les ventes pas encore synchronisées restent sur le téléphone.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmForgot = false; onForgot() }) { Text("Se déconnecter") } },
            dismissButton = { TextButton(onClick = { confirmForgot = false }) { Text("Annuler") } },
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Image(painterResource(R.drawable.enkiba_logo), contentDescription = "Enkiba", modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(Spacing.lg))
        val name = displayFirstName(firstName, null)
        Text(if (name.isNullOrBlank()) "Bonjour" else "Bonjour $name", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (onBiometric != null) "Saisissez votre code ou utilisez votre empreinte" else "Saisissez votre code",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        PinDots(filled = state.entered.length, isError = state.error != null)
        Text(
            state.error ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.md).height(36.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        PinPad(onDigit = onDigit, onDelete = onDelete, onBiometric = onBiometric)
        Spacer(Modifier.height(Spacing.lg))
        TextButton(onClick = { confirmForgot = true }) { Text("Code oublié ?") }
    }
}

@Preview
@Composable
private fun LockScreenPreview() {
    EnkibaTheme {
        LockScreen(LockUiState(entered = "12", error = "Code incorrect. Encore 3 essais."), "Awa", {}, {}, {}, {})
    }
}

