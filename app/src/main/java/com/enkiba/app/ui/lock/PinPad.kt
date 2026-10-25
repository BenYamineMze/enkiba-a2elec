package com.enkiba.app.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enkiba.app.security.AppLock

/** Points du code : pleins pour les chiffres saisis. */
@Composable
fun PinDots(filled: Int, isError: Boolean, modifier: Modifier = Modifier) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Row(
        modifier.semantics { contentDescription = "$filled chiffres sur ${AppLock.PIN_LENGTH}" },
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(AppLock.PIN_LENGTH) { i ->
            Box(
                Modifier.size(16.dp).clip(CircleShape)
                    .background(if (i < filled) color else MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, if (i < filled) color else MaterialTheme.colorScheme.outline, CircleShape),
            )
        }
    }
}

/** Pavé numérique ; la touche empreinte n'apparaît que si [onBiometric] est fourni. */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { d -> Key(onClick = { onDigit(d) }) { Text("$d", style = MaterialTheme.typography.headlineSmall) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (onBiometric != null) {
                Key(onClick = onBiometric, filled = false) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = "Empreinte", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                }
            } else {
                Box(Modifier.size(KEY))
            }
            Key(onClick = { onDigit('0') }) { Text("0", style = MaterialTheme.typography.headlineSmall) }
            Key(onClick = onDelete, filled = false) {
                Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Effacer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val KEY = 72.dp

@Composable
private fun Key(onClick: () -> Unit, filled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        Modifier.size(KEY).clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
