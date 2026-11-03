package com.enkiba.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.enkiba.app.security.AppLock
import com.enkiba.app.ui.components.EnkibaCard
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SecondaryButton
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

@Composable
fun PinSettingsScreen(
    state: PinSettingsUiState,
    onBack: () -> Unit,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onBiometric: (Boolean) -> Unit,
    onChange: () -> Unit,
    onDisable: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "Code PIN", onBack = onBack)
        if (state.step == PinStep.OVERVIEW) Overview(state, onBiometric, onChange, onDisable)
        else Entry(state, onDigit, onDelete)
    }
}

@Composable
private fun Overview(state: PinSettingsUiState, onBiometric: (Boolean) -> Unit, onChange: () -> Unit, onDisable: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.screen), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        EnkibaCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("Code PIN activé", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Enkiba demande votre code à l'ouverture et après ${AppLock.TIMEOUT_MS / 60_000} minutes en arrière-plan. " +
                        "Après ${AppLock.MAX_FAILURES} erreurs, vous devrez vous reconnecter avec votre mot de passe.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.biometricAvailable) {
            EnkibaCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().toggleable(value = state.biometricOn, role = Role.Switch, onValueChange = onBiometric),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Déverrouiller avec l'empreinte", style = MaterialTheme.typography.titleSmall)
                        Text("Ou le visage, selon le téléphone. Le code reste toujours possible.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.biometricOn, onCheckedChange = null)
                }
            }
        }
        SecondaryButton("Changer le code", onClick = onChange, modifier = Modifier.fillMaxWidth())
        SecondaryButton(
            "Désactiver le code",
            onClick = onDisable,
            modifier = Modifier.fillMaxWidth(),
            contentColor = EnkibaTheme.colors.danger.content,
        )
    }
}

@Composable
private fun Entry(state: PinSettingsUiState, onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val (title, hint) = when (state.step) {
        PinStep.CURRENT -> "Code actuel" to "Saisissez votre code actuel"
        PinStep.CONFIRM -> "Confirmez le code" to "Saisissez-le une seconde fois"
        else -> (if (state.enabled) "Nouveau code" else "Choisissez un code") to
            "${AppLock.PIN_LENGTH} chiffres, demandés à l'ouverture d'Enkiba"
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
        PinPad(onDigit = onDigit, onDelete = onDelete)
    }
}

@Preview
@Composable
private fun PinOverviewPreview() {
    EnkibaTheme {
        PinSettingsScreen(PinSettingsUiState(enabled = true, biometricAvailable = true, biometricOn = true, step = PinStep.OVERVIEW), {}, {}, {}, {}, {}, {})
    }
}
