package com.enkiba.app.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role

/** Choix Système / Clair / Sombre, appliqué dès qu'une option est touchée. */
@Composable
fun ThemeChoiceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current by ThemePreferences.mode(context).collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thème") },
        text = {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Spacing.touch)
                            .selectable(selected = mode == current, role = Role.RadioButton) { ThemePreferences.set(context, mode) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        RadioButton(selected = mode == current, onClick = null)
                        Column {
                            Text(mode.label, style = MaterialTheme.typography.bodyLarge)
                            if (mode == ThemeMode.SYSTEM) {
                                Text("Suit le réglage du téléphone", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
