package com.enkiba.app.ui.users

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enkiba.app.data.repository.TeamMember
import com.enkiba.app.ui.components.EmptyState
import com.enkiba.app.ui.components.ScreenHeader
import com.enkiba.app.ui.components.SquareIconButton
import com.enkiba.app.ui.components.StatusChip
import com.enkiba.app.ui.components.initialsOf
import com.enkiba.app.ui.components.softShadow
import com.enkiba.app.ui.theme.EnkibaTheme
import com.enkiba.app.ui.theme.Spacing

data class TeamActions(
    val onBack: () -> Unit = {},
    val onAdd: () -> Unit = {},
    val onEdit: (TeamMember) -> Unit = {},
    val onToggle: (TeamMember, Boolean) -> Unit = { _, _ -> },
    val onConfirmDeactivate: () -> Unit = {},
    val onCancelDeactivate: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(state: TeamUiState, myUserId: String?, actions: TeamActions) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); actions.onMessageShown() } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Équipe",
                subtitle = if (state.loading || state.error != null) null
                else "${state.activeSellers} vendeur${if (state.activeSellers > 1) "s" else ""} actif${if (state.activeSellers > 1) "s" else ""}",
                onBack = actions.onBack,
                actions = { SquareIconButton(Icons.Rounded.PersonAdd, "Ajouter un vendeur", onClick = actions.onAdd) },
            )
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = actions.onRefresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    state.error != null -> LazyColumn(Modifier.fillMaxSize()) {
                        item { EmptyState(state.error, actionLabel = "Réessayer", onAction = actions.onRetry) }
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xl),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.members, key = { it.userId }) { m ->
                            MemberRow(m, isMe = m.userId == myUserId, busy = state.busyUserId == m.userId, actions = actions)
                        }
                        item {
                            Text(
                                "Un vendeur désactivé ne peut plus se connecter ; ses ventes restent dans l'historique.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
    }

    state.confirmDeactivate?.let { m ->
        AlertDialog(
            onDismissRequest = actions.onCancelDeactivate,
            title = { Text("Désactiver ${m.fullName} ?") },
            text = { Text("Ce vendeur ne pourra plus se connecter. Vous pourrez le réactiver à tout moment.") },
            confirmButton = { TextButton(onClick = actions.onConfirmDeactivate) { Text("Désactiver", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = actions.onCancelDeactivate) { Text("Annuler") } },
        )
    }
}

@Composable
private fun MemberRow(m: TeamMember, isMe: Boolean, busy: Boolean, actions: TeamActions) {
    val colors = EnkibaTheme.colors
    val shape = MaterialTheme.shapes.large
    val tile = if (!m.active) colors.neutral else if (m.isAdmin) colors.mint else colors.sky
    Row(
        Modifier.fillMaxWidth().softShadow(shape, 4.dp).clip(shape).background(MaterialTheme.colorScheme.surface)
            // Seuls les vendeurs se modifient ici ; l'admin passe par « Mon profil »
            .then(if (!m.isAdmin) Modifier.clickable(role = Role.Button, onClickLabel = "Modifier") { actions.onEdit(m) } else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(46.dp).clip(MaterialTheme.shapes.medium).background(tile.container), contentAlignment = Alignment.Center) {
            Text(initialsOf(m.firstName, m.lastName), style = MaterialTheme.typography.titleSmall, color = tile.content)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(if (isMe) "${m.fullName} (vous)" else m.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(m.email, m.phone).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(if (m.isAdmin) "Admin" else "Vendeur", if (m.isAdmin) colors.mint else colors.sky)
                if (!m.active) StatusChip("Désactivé", colors.neutral)
            }
        }
        if (!m.isAdmin) {
            if (busy) CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary)
            else Switch(
                checked = m.active,
                onCheckedChange = { actions.onToggle(m, it) },
                modifier = Modifier.semantics { contentDescription = if (m.active) "Désactiver ${m.fullName}" else "Activer ${m.fullName}" },
            )
        }
    }
}
