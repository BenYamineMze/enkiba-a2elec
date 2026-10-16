package com.enkiba.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** État de la synchronisation affiché par la pastille du bouton. */
enum class SyncState(val label: String) {
    SYNCED("à jour"),
    PENDING("modifications en attente"),
    SYNCING("en cours"),
    OFFLINE("hors ligne"),
}

/** « Vendredi 16 octobre » (date du jour, en français). */
fun todayLabel(date: Date = Date()): String =
    SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(date).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/** Initiales pour l'avatar : « Mze Ben » → « MB ». */
fun initialsOf(firstName: String?, lastName: String?): String {
    val f = firstName?.trim()?.firstOrNull()?.uppercaseChar()
    val l = lastName?.trim()?.firstOrNull()?.uppercaseChar()
    return listOfNotNull(f, l).joinToString("").ifEmpty { "?" }
}

/**
 * En-tête de l'accueil : date, salutation, rôle, bouton de synchronisation avec pastille
 * d'état, avatar (initiales) qui ouvre le menu utilisateur.
 */
@Composable
fun UserHeader(
    firstName: String?,
    lastName: String?,
    roleLabel: String?,
    syncState: SyncState,
    onSync: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
    dateLabel: String = todayLabel(),
) {
    val colors = EnkibaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(dateLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = if (firstName.isNullOrBlank()) "Bonjour" else "Bonjour, ${firstName.trim()}",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (!roleLabel.isNullOrBlank()) StatusChip(roleLabel, colors.mint)
        }

        // Synchronisation : pastille verte (à jour), orange (en attente), grise (hors ligne)
        val dot: Color = when (syncState) {
            SyncState.SYNCED -> MaterialTheme.colorScheme.primary
            SyncState.PENDING, SyncState.SYNCING -> colors.warning
            SyncState.OFFLINE -> MaterialTheme.colorScheme.outline
        }
        Box {
            SquareIconButton(Icons.Rounded.Sync, "Synchroniser : ${syncState.label}", onClick = onSync)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dot)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
        }

        // Avatar : initiales, ouvre le menu (profil, déconnexion)
        val initials = initialsOf(firstName, lastName)
        Box(
            modifier = Modifier
                .size(Spacing.touch)
                .clip(MaterialTheme.shapes.small)
                .background(colors.mint.container)
                .clickable(role = Role.Button, onClick = onAvatarClick)
                .semantics { contentDescription = "Menu utilisateur" },
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = MaterialTheme.typography.labelLarge, color = colors.link)
        }
    }
}
